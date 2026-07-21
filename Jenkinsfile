// Armor HUD CI — builds the whole Stonecutter/Architectury matrix, verifies it, and publishes.
//
// Two things this is for:
//
//   1. A change lands -> build every loader/version, collect the jars, run every check that can run
//      unattended, and report. Progress goes to ntfy as it happens, so the state of a long run is
//      visible from a phone without opening Jenkins.
//   2. When the results look right -> publish, on request only. GitHub release with the supplied
//      changelog, and the same notes to Modrinth across the matrix.
//
// No SNAPSHOT parameter yet, deliberately: snapshots are currently on the 26.3 line, and the whole
// 26.x line is unbuildable because architectury-loom has no unobfuscated/no-remap support (26.1+
// ships no Mojang mappings and no Fabric intermediary). A snapshot build would fail for that reason
// alone, so it is gated on 26.x support landing.

// Push a line to ntfy. Deliberately terse: these are read on a phone, so they lead with the thing
// worth acting on. Never fails the build — a notification problem is not a build problem.
def notify(String title, String message, String tags = 'gear', String priority = 'default') {
    if (!params.NOTIFY_URL?.trim()) { return }
    // Values go through the environment rather than the command line so a changelog or an error
    // message containing quotes cannot break the shell or inject into it.
    withEnv(["NTFY_TITLE=${title}", "NTFY_BODY=${message}", "NTFY_TAGS=${tags}", "NTFY_PRIO=${priority}"]) {
        sh '''
            curl -sS -X POST \
                -H "Title: $NTFY_TITLE" \
                -H "Tags: $NTFY_TAGS" \
                -H "Priority: $NTFY_PRIO" \
                -d "$NTFY_BODY" \
                "''' + params.NOTIFY_URL + '''" >/dev/null 2>&1 || true
        '''
    }
}

pipeline {
    agent { label 'linux' }

    parameters {
        booleanParam(name: 'RUN_JAR_AUDIT', defaultValue: true,
                description: 'Static audit of every built jar: manifest shape, java target, guard ' +
                        'branches, resource paths. Seconds, no game launch.')
        booleanParam(name: 'RUN_HUD_CHECK', defaultValue: true,
                description: 'In-world HUD pixel assertions across the whole matrix. Launches a real ' +
                        'client per node under software GL — this is the long one.')
        booleanParam(name: 'RUN_CONFIG_CHECK', defaultValue: false,
                description: 'Drive the mod list to the config screen using real jars in a launcher. ' +
                        'Needs PrismLauncher instances on the agent — not provisioned yet.')
        booleanParam(name: 'RUN_SCREENSHOT_TESTS', defaultValue: false,
                description: 'Fabric Client GameTest screenshot tests (1.21.5+ only).')

        booleanParam(name: 'PUBLISH_GITHUB', defaultValue: false,
                description: 'Create a GitHub release for mod.version and attach every jar.')
        booleanParam(name: 'PUBLISH_MODRINTH', defaultValue: false,
                description: 'Upload every jar to Modrinth (needs the modrinth-token credential).')
        text(name: 'CHANGELOG', defaultValue: '',
                description: 'Release notes. Used as the GitHub release body and as the Modrinth ' +
                        'changelog on every uploaded version. Markdown.')

        booleanParam(name: 'DIAGNOSE_HUD', defaultValue: false,
                description: 'Debug loop for the HUD check: skips the matrix build, probes the ' +
                        'agent\'s OpenGL, and runs ONE node. Minutes instead of an hour.')
        string(name: 'DIAGNOSE_NODE', defaultValue: 'neoforge 1.21.11',
                description: 'Which node DIAGNOSE_HUD runs, as "<loader> <mc>".')

        string(name: 'NOTIFY_URL', defaultValue: 'https://notify.saolghra.co.uk/builds',
                description: 'ntfy topic for progress notifications. Empty disables them.')
    }

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '50', daysToKeepStr: '365'))
    }

    environment {
        // Deliberately OUTSIDE the workspace: cleanWs() runs after every build, so a cache under
        // ${WORKSPACE} is destroyed each time and every run re-downloads Minecraft, the mappings and
        // every dependency — which also makes the build hostage to third-party maven uptime.
        GRADLE_USER_HOME = "${JENKINS_HOME}/.gradle-armor-hud"
        _JAVA_OPTIONS = '-Xmx3G -Xms512M'
    }

    // Build when something actually changes, not on a clock. A nightly would re-verify code that
    // has not moved since the last run — hours of agent time to re-confirm a known answer. Polling
    // rather than a webhook so nothing has to be configured on the GitHub side, and so it keeps
    // working if the endpoint moves; the cost is that a push is picked up within five minutes
    // rather than instantly, which does not matter for a run measured in hours.
    triggers { pollSCM('H/5 * * * *') }

    stages {
        stage('Setup JDK 21') {
            steps {
                script {
                    notify("Armor HUD #${env.BUILD_NUMBER} started", 'build + verify', 'hammer')
                }
                sh '''
                    set -e
                    # Gradle 8.14 needs Java <= 21; provision Temurin 21 locally if the agent lacks it.
                    JDK_DIR="$WORKSPACE/.jdk/temurin-21"
                    if [ ! -x "$JDK_DIR"/bin/javac ]; then
                        mkdir -p "$JDK_DIR"
                        curl -sSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
                            -o "$WORKSPACE/.jdk/jdk21.tar.gz"
                        tar -xzf "$WORKSPACE/.jdk/jdk21.tar.gz" -C "$JDK_DIR" --strip-components=1
                        rm -f "$WORKSPACE/.jdk/jdk21.tar.gz"
                    fi
                    chmod +x gradlew
                    JAVA_HOME="$JDK_DIR" ./gradlew --version
                '''
            }
        }

        stage('Build matrix') {
            when { expression { return !params.DIAGNOSE_HUD } }
            steps {
                // Retried because the upstream mod mavens are not reliable: a single transient
                // artifact download failure otherwise reds the entire matrix.
                retry(2) {
                    sh '''
                        set -e
                        export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                        export PATH="$JAVA_HOME/bin:$PATH"
                        # chiseledBuild iterates the whole version x loader matrix from settings.gradle.kts,
                        # generating each version's source (a direct :loader:version:build would be empty).
                        ./gradlew chiseledBuild -x runGameTest -x runClientGameTest --stacktrace
                    '''
                }
                script {
                    def jars = sh(script: 'ls build/libs/*/*.jar 2>/dev/null | grep -vc sources || echo 0',
                                  returnStdout: true).trim()
                    notify("Build OK — ${jars} jars", "matrix built, starting checks", 'package')
                }
            }
        }

        stage('Unit tests') {
            when { expression { return !params.DIAGNOSE_HUD } }
            steps {
                sh '''
                    set -e
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    # ArmorHudMath is version-independent; one node is enough.
                    ./gradlew :1.21.5:test --stacktrace
                '''
            }
            post {
                always { junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml' }
                failure { script { notify("Unit tests FAILED", "build #${env.BUILD_NUMBER}", 'x', 'high') } }
            }
        }

        stage('Screenshot tests') {
            when { expression { return params.RUN_SCREENSHOT_TESTS } }
            steps {
                sh '''
                    set -e
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    export DISPLAY="${DISPLAY:-:0}" __GL_SYNC_TO_VBLANK=0 vblank_mode=0
                    ./gradlew :fabric:1.21.5:runClientGameTest --stacktrace
                '''
            }
            post { always { archiveArtifacts artifacts: '**/run/clientGameTest/screenshots/*.png', allowEmptyArchive: true } }
        }

        // The verification harness lives in a separate private repo: it is test tooling, not part of
        // the published mod. Cloned read-only with a deploy key scoped to that one repo.
        stage('Fetch verification harness') {
            when { expression { return params.RUN_JAR_AUDIT || params.RUN_HUD_CHECK || params.RUN_CONFIG_CHECK || params.DIAGNOSE_HUD } }
            steps {
                // Jenkins verifies SSH host keys against the agent's known_hosts and refuses a host
                // it has never seen. Seed it from GitHub's published list rather than weakening
                // verification or pinning keys that eventually rotate. No python3 here: the stock
                // agent image does not have it.
                sh '''
                    set -e
                    mkdir -p ~/.ssh && chmod 700 ~/.ssh
                    touch ~/.ssh/known_hosts && chmod 600 ~/.ssh/known_hosts
                    if ! grep -q "^github.com " ~/.ssh/known_hosts 2>/dev/null; then
                        curl -sS https://api.github.com/meta \
                            | tr ',' '\\n' \
                            | grep -oE '"(ssh-[a-z0-9]+|ecdsa-sha2-nistp256) [A-Za-z0-9+/=]+"' \
                            | tr -d '"' | sed 's/^/github.com /' >> ~/.ssh/known_hosts
                        echo "seeded known_hosts with GitHub's published host keys"
                    fi
                '''
                dir('verify') {
                    checkout([$class: 'GitSCM',
                        branches: [[name: '*/main']],
                        userRemoteConfigs: [[
                            url: 'git@github.com:SaolGhra/armor-hud-verify.git',
                            credentialsId: 'armor-hud-verify-key']]])
                }
                // Record which harness produced the results: a stale checkout is otherwise invisible
                // and its output looks exactly like a current one.
                sh 'cd verify && git rev-parse --short HEAD | sed "s/^/harness /"'
            }
        }

        stage('Audit jars') {
            when { expression { return params.RUN_JAR_AUDIT && !params.DIAGNOSE_HUD } }
            steps {
                sh '''
                    set -e
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    # Agents are disposable containers, so python3 is installed per build rather than
                    # baked into an image or installed on the host — host packages are not in the
                    # container. Nothing persists: the container goes away with the build.
                    if ! command -v python3 >/dev/null 2>&1; then
                        echo "installing python3 (absent from this agent image)"
                        if command -v apt-get >/dev/null 2>&1; then
                            apt-get update -qq && apt-get install -y -qq python3 >/dev/null
                        elif command -v apk >/dev/null 2>&1; then
                            apk add --no-cache python3 >/dev/null
                        fi
                        command -v python3 >/dev/null || {
                            echo "!! could not install python3 — the jar audit needs it"; exit 1; }
                    fi
                    export ARMOR_HUD_ROOT="$WORKSPACE"
                    python3 verify/audit_jars.py | tee build/audit.txt
                '''
                script {
                    def line = sh(script: "grep -E 'jars,' build/audit.txt | tail -1", returnStdout: true).trim()
                    notify("Jar audit: ${line}", "build #${env.BUILD_NUMBER}",
                           line.contains('0 failed') ? 'white_check_mark' : 'x',
                           line.contains('0 failed') ? 'default' : 'high')
                }
            }
            post { always { archiveArtifacts artifacts: 'build/audit.txt', allowEmptyArchive: true } }
        }

        stage('HUD check') {
            when { expression { return params.RUN_HUD_CHECK && !params.DIAGNOSE_HUD } }
            steps {
                script { notify("HUD check started", "37 nodes, software GL — this takes a while", 'hourglass') }
                sh '''
                    set -e
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    export ARMOR_HUD_HEADLESS=1
                    export ARMOR_HUD_ROOT="$WORKSPACE"

                    # This check launches a real client per node, so the agent needs a display server,
                    # an input tool, ImageMagick and a GL stack. Installed per run because agents are
                    # disposable; worth it here in a way it would not be for a per-commit stage, since
                    # the check itself runs for hours.
                    #
                    # The X libraries are for LWJGL: it ships its own natives but links against the
                    # system X client libraries, and Minecraft dies at window creation without them.
                    if ! command -v Xvfb >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
                        echo "installing the display stack (absent from this agent image)"
                        apt-get update -qq
                        apt-get install -y -qq \
                            python3 xvfb xdotool openbox imagemagick \
                            libgl1-mesa-dri libglu1-mesa mesa-utils \
                            libxext6 libxrender1 libxtst6 libxi6 libxrandr2 \
                            libxcursor1 libxinerama1 libxxf86vm1 >/dev/null
                    fi
                    for tool in Xvfb xdotool import python3; do
                        command -v "$tool" >/dev/null 2>&1 || {
                            echo "!! $tool still missing after install — cannot run the HUD check"; exit 1; }
                    done

                    # Smoke one node first. If the client cannot start here it cannot start on any
                    # of them, and finding that out 37 boot-timeouts later wastes hours and usually
                    # ends with the agent being killed rather than a usable error.
                    if ! verify/hud_ingame.sh neoforge 1.21.11 | tee build/hud-check.txt; then
                        echo "!! the first node failed — not attempting the rest"
                        exit 1
                    fi

                    { verify/hud_ingame.sh neoforge
                      verify/hud_ingame.sh fabric
                      verify/hud_ingame.sh forge
                    } | tee -a build/hud-check.txt
                '''
                script {
                    def pass = sh(script: "grep -c '  PASS' build/hud-check.txt || echo 0", returnStdout: true).trim()
                    def fail = sh(script: "grep -c '  FAIL' build/hud-check.txt || echo 0", returnStdout: true).trim()
                    notify("HUD check: ${pass} passed, ${fail} failed", "build #${env.BUILD_NUMBER}",
                           fail == '0' ? 'white_check_mark' : 'rotating_light',
                           fail == '0' ? 'default' : 'high')
                }
            }
            post {
                always {
                    // Archive the per-node client logs too, not just the captures. When a node fails
                    // to reach a live state the reason is only in its log, and cleanWs() removes the
                    // workspace before anyone can look — which cost three multi-hour runs.
                    archiveArtifacts artifacts: 'build/hud-check.txt,build/hud-screenshots/*.png,build/hud-screenshots/*.log',
                                     allowEmptyArchive: true
                }
            }
        }

        // Fast debug loop for the HUD check. The full stage rebuilds 37 nodes before it gets
        // anywhere near a client, which is a poor way to chase a problem that shows up in the first
        // ten seconds of one. This skips the matrix, proves the display and GL stack on its own
        // terms, then runs exactly one node.
        stage('Diagnose HUD') {
            when { expression { return params.DIAGNOSE_HUD } }
            steps {
                sh '''
                    set -e
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    export ARMOR_HUD_ROOT="$WORKSPACE"

                    if ! command -v Xvfb >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
                        echo "=== installing the display stack"
                        apt-get update -qq
                        apt-get install -y -qq \
                            python3 xvfb xdotool openbox imagemagick \
                            libgl1-mesa-dri libglu1-mesa mesa-utils \
                            libxext6 libxrender1 libxtst6 libxi6 libxrandr2 \
                            libxcursor1 libxinerama1 libxxf86vm1 >/dev/null
                    fi

                    # Prove the GL stack independently of Minecraft. If llvmpipe cannot offer a 3.2
                    # core profile then no amount of Minecraft debugging matters, and glxinfo says so
                    # in one line — where the client only ever says "never went live".
                    echo "=== GL probe"
                    Xvfb :88 -screen 0 1920x1080x24 -nolisten tcp >/dev/null 2>&1 &
                    XVFB_PID=$!
                    sleep 3
                    if DISPLAY=:88 LIBGL_ALWAYS_SOFTWARE=1 glxinfo -B 2>&1 | head -20; then
                        echo "--- glxinfo ran"
                    else
                        echo "!! glxinfo failed — the agent has no usable GL"
                    fi
                    DISPLAY=:88 LIBGL_ALWAYS_SOFTWARE=1 glxinfo 2>/dev/null \
                        | grep -iE "OpenGL core profile version|OpenGL version|renderer string" | head -3
                    kill $XVFB_PID 2>/dev/null || true

                    # Build only the node under test. hud_ingame.sh sets it active and runs its
                    # client, so chiseledBuild is unnecessary here — that is the 35 minutes saved.
                    set -- $DIAGNOSE_NODE
                    LOADER="$1"; MC="$2"
                    echo "=== building $LOADER:$MC only (with the verify hook compiled in)"
                    # -Parmorhud.verify=true on the set-active is what compiles ArmorHudVerifyHook in
                    # — the const is resolved when the version is set active, not at runClient. Match
                    # what hud_ingame.sh does so the pre-build is reused rather than rebuilt.
                    ./gradlew --console=plain -q -Parmorhud.verify=true "Set active project to $MC"
                    ./gradlew -Parmorhud.verify=true ":$LOADER:$MC:build" -x test --stacktrace

                    echo "=== running one node: $LOADER $MC"
                    # Software rendering needs longer between "in the world" and a frame worth
                    # asserting on than a GPU does.
                    export ARMOR_HUD_HEADLESS=1 SETTLE="${SETTLE:-60}"

                    # Log every window on every display, every 4s, for the whole node — a timeline of
                    # when (or whether) Minecraft's main 1920x1080 window ever appears. The harness
                    # picks its display dynamically (:77+), so scan a range. Fully guarded for set -e.
                    ( for _ in $(seq 1 45); do
                        for d in :77 :78 :79 :80; do
                            ids=$(DISPLAY=$d xdotool search --all "" 2>/dev/null || true)
                            for w in $ids; do
                                g=$(DISPLAY=$d xdotool getwindowgeometry "$w" 2>/dev/null | grep -oE "[0-9]+x[0-9]+" | tail -1 || true)
                                [ "$g" = "1x1" ] && continue
                                echo "  [win $d] wid=$w geom=$g" >&2
                            done
                        done
                        echo "  [win ---- $(date +%H:%M:%S)]" >&2
                        sleep 4
                      done ) &
                    WMON=$!

                    verify/hud_ingame.sh "$LOADER" "$MC" || true
                    kill $WMON 2>/dev/null || true

                    # Describe the capture in the console. Artifacts have repeatedly not been there
                    # when needed, and "is the frame black, a loading screen, or the world?" is the
                    # whole question when every bar reads zero pixels.
                    echo "=== capture stats"
                    SHOT="build/hud-screenshots/$LOADER-$MC-hud.png"
                    if [ -f "$SHOT" ]; then
                        magick identify "$SHOT" 2>/dev/null || identify "$SHOT" 2>/dev/null
                        echo "    mean brightness (0=black, 65535=white):"
                        magick "$SHOT" -format "      %[mean]" info: 2>/dev/null && echo
                        echo "    distinct colours (a loading screen has very few):"
                        magick "$SHOT" -format "      %k" info: 2>/dev/null && echo
                        # Emit a downscaled copy as base64 so the actual image can be seen from the
                        # console — artifacts have been unreliable, and "what is in the frame" is the
                        # whole question. Decode with: grep B64IMG log | cut -d' ' -f2 | base64 -d > x.png
                        magick "$SHOT" -resize 480x /tmp/diag_small.png 2>/dev/null
                        echo "B64IMG $(base64 -w0 /tmp/diag_small.png 2>/dev/null)"
                    else
                        echo "    (no capture written)"
                    fi

                    echo "=== any PNG screenshots anywhere in the workspace"
                    find "$WORKSPACE" -name "*.png" -path "*screenshots*" 2>/dev/null | head -10 || true
                    find "$WORKSPACE" -type d -name screenshots 2>/dev/null | head || true

                    echo "=== verify-hook lines from the client log"
                    grep -F "[armor_hud verify]" build/hud-screenshots/$LOADER-$MC.log 2>/dev/null || echo "(hook printed nothing — it never fired)"

                    echo "=== client log tail"
                    tail -40 build/hud-screenshots/$LOADER-$MC.log 2>/dev/null || echo "(no client log)"
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'build/hud-screenshots/*', allowEmptyArchive: true
                }
            }
        }

        stage('Collect jars') {
            when { expression { return !params.DIAGNOSE_HUD } }
            steps {
                archiveArtifacts artifacts: 'build/libs/**/*.jar', fingerprint: true, excludes: '**/*-sources.jar'
            }
        }

        // Publishing is opt-in and never part of a verification run: it happens when someone has
        // looked at the results and decided they are good.
        stage('Publish to GitHub') {
            when { expression { return params.PUBLISH_GITHUB } }
            steps {
                script { notify("Publishing to GitHub…", "release for the current mod.version", 'rocket') }
                // The changelog goes through a file rather than the command line: release notes are
                // multi-line and contain quotes and backticks, which would be mangled or would break
                // the shell if interpolated into it.
                writeFile file: 'build/changelog.md', text: params.CHANGELOG ?: ''
                withCredentials([string(credentialsId: 'github-token', variable: 'GH_TOKEN')]) {
                    sh '''
                        set -e
                        VERSION=$(grep -E '^mod\\.version=' gradle.properties | cut -d= -f2)
                        REPO="SaolGhra/Armor-Hud"
                        TAG="v$VERSION"
                        echo "creating release $TAG on $REPO"

                        # Refuse rather than silently make a second release for a tag that exists.
                        EXISTING=$(curl -sS -o /dev/null -w '%{http_code}' \
                            -H "Authorization: Bearer $GH_TOKEN" \
                            "https://api.github.com/repos/$REPO/releases/tags/$TAG")
                        if [ "$EXISTING" = "200" ]; then
                            echo "!! release $TAG already exists — bump mod.version or delete it first"
                            exit 1
                        fi

                        # Build the request body with python so the changelog is JSON-escaped properly.
                        command -v python3 >/dev/null 2>&1 || { apt-get update -qq && apt-get install -y -qq python3 >/dev/null; }
                        python3 - "$TAG" "$VERSION" > build/release.json <<'PY'
import json, sys
tag, version = sys.argv[1], sys.argv[2]
body = open('build/changelog.md').read().strip() or f'Armor HUD {version}'
print(json.dumps({'tag_name': tag, 'name': f'Armor HUD {version}',
                  'body': body, 'draft': False, 'prerelease': False}))
PY
                        UPLOAD=$(curl -sS -X POST \
                            -H "Authorization: Bearer $GH_TOKEN" \
                            -H "Content-Type: application/json" \
                            -d @build/release.json \
                            "https://api.github.com/repos/$REPO/releases" \
                            | python3 -c "import json,sys; print(json.load(sys.stdin)['upload_url'].split('{')[0])")

                        # Attach every built jar, sources excluded.
                        COUNT=0
                        for jar in build/libs/*/*.jar; do
                            case "$jar" in *-sources.jar) continue;; esac
                            name=$(basename "$jar")
                            curl -sS -X POST \
                                -H "Authorization: Bearer $GH_TOKEN" \
                                -H "Content-Type: application/java-archive" \
                                --data-binary @"$jar" \
                                "$UPLOAD?name=$name" >/dev/null
                            COUNT=$((COUNT+1))
                        done
                        echo "attached $COUNT jars to $TAG"
                    '''
                }
                script { notify("GitHub release published", "check the releases page", 'white_check_mark') }
            }
        }

        stage('Publish to Modrinth') {
            when { expression { return params.PUBLISH_MODRINTH } }
            steps {
                script { notify("Publishing to Modrinth…", "uploading the matrix", 'rocket') }
                writeFile file: 'build/changelog.md', text: params.CHANGELOG ?: ''
                withCredentials([string(credentialsId: 'modrinth-token', variable: 'MODRINTH_TOKEN')]) {
                    sh '''
                        set -e
                        export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                        export PATH="$JAVA_HOME/bin:$PATH"
                        # chiseledPublish runs publishMods for every version (each with its source active).
                        if [ -s build/changelog.md ]; then
                            ./gradlew chiseledPublish -Pchangelog="$(cat build/changelog.md)" --stacktrace
                        else
                            ./gradlew chiseledPublish --stacktrace
                        fi
                    '''
                }
                script { notify("Modrinth publish done", "every version uploaded", 'white_check_mark') }
            }
        }
    }

    post {
        success {
            script {
                notify("Armor HUD #${env.BUILD_NUMBER} SUCCESS",
                       "${currentBuild.durationString.replace(' and counting', '')}",
                       'white_check_mark')
            }
        }
        failure {
            script {
                notify("Armor HUD #${env.BUILD_NUMBER} FAILED",
                       "${env.BUILD_URL}", 'rotating_light', 'high')
            }
        }
        always { cleanWs() }
    }
}
