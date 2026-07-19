// Armor HUD CI — builds the whole Stonecutter/Architectury matrix and (optionally) publishes to
// Modrinth. Replaces the old single-version "bump gradle.properties" pipeline: with the monorepo,
// every supported Minecraft version is a Gradle node, so CI just iterates the matrix.
//
// See the project docs for why the toolchain is pinned the way it is.
//
// No SNAPSHOT parameter yet, deliberately: snapshots are currently on the 26.3 line, and the whole
// 26.x line is unbuildable because architectury-loom has no unobfuscated/no-remap support (26.1+ ships
// no Mojang mappings and no Fabric intermediary — see the project docs). A snapshot build
// would fail for that reason alone, so it is gated on 26.x support landing. When it does, add a
// TARGET_MINECRAFT_VERSION param that writes versions/<mc>/gradle.properties and appends the version
// in settings.gradle.kts before running chiseledBuild.

// True when Jenkins itself started this build on a schedule, rather than a person clicking Build.
// This is how the nightly run turns on the expensive checks without the parameterized-scheduler
// plugin: a manual build stays fast, a timed one verifies everything.
def nightly() {
    return currentBuild.getBuildCauses().any { it._class?.contains('TimerTrigger') }
}

pipeline {
    agent { label 'linux' }

    parameters {
        booleanParam(name: 'PUBLISH', defaultValue: false,
                description: 'Publish the built jars to Modrinth (needs the modrinth-token credential).')
        text(name: 'CHANGELOG', defaultValue: '',
                description: 'Release notes for Modrinth, shown on every uploaded version. Markdown. ' +
                        'Left empty, the version pages link to the GitHub releases page instead.')
        booleanParam(name: 'RUN_SCREENSHOT_TESTS', defaultValue: false,
                description: 'Run the Fabric Client GameTest screenshot tests (needs a display / Xvfb).')
        booleanParam(name: 'RUN_JAR_AUDIT', defaultValue: true,
                description: 'Static audit of every built jar. Seconds, no game launch.')
        booleanParam(name: 'RUN_HUD_CHECK', defaultValue: false,
                description: 'In-world HUD pixel assertions across the whole matrix. Launches a real ' +
                        'client per node under software GL — roughly 90 minutes. Nightly / pre-release.')
        booleanParam(name: 'RUN_CONFIG_CHECK', defaultValue: false,
                description: 'Drive the mod list to the config screen using real jars in a launcher. ' +
                        'Needs PrismLauncher instances on the agent — not yet provisioned.')
        string(name: 'NOTIFY_URL', defaultValue: 'https://notify.saolghra.co.uk/builds',
                description: 'Webhook pinged on success/failure.')
    }

    // Uncomment to have the full verification run itself overnight. Deliberately off by default:
    // this is roughly two hours of agent time every night, which is a commitment to make on purpose.
    //   triggers { cron('H 3 * * *') }

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '50', daysToKeepStr: '365'))
    }

    environment {
        // Deliberately OUTSIDE the workspace: cleanWs() runs after every build, so a cache under
        // ${WORKSPACE} is destroyed each time and every run re-downloads Minecraft, the mappings and
        // every dependency. That is slow, and it makes the build hostage to third-party maven uptime
        // — a flaky maven.terraformersmc.com (HTTP/2 resets) failed the whole 37-node matrix on a
        // single optional Mod Menu jar that is already cached on any warm machine.
        GRADLE_USER_HOME = "${JENKINS_HOME}/.gradle-armor-hud"
        _JAVA_OPTIONS = '-Xmx3G -Xms512M'
    }

    stages {
        stage('Setup JDK 21') {
            steps {
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
            steps {
                // Retried because the upstream mod mavens are not reliable: a single transient
                // artifact download failure otherwise reds the entire matrix. The retry costs
                // nothing on a warm cache, since resolved artifacts are already local.
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
            }
        }

        stage('Unit tests') {
            steps {
                sh '''
                    set -e
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    # ArmorHudMath is version-independent; one node is enough.
                    ./gradlew :1.21.5:test --stacktrace
                '''
            }
            post { always { junit allowEmptyResults: true, testResults: '**/build/test-results/test/*.xml' } }
        }

        stage('Screenshot tests') {
            when { expression { return params.RUN_SCREENSHOT_TESTS } }
            steps {
                sh '''
                    set -e
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    # Client gametests need a framebuffer + vsync off (see the project docs).
                    export DISPLAY="${DISPLAY:-:0}" __GL_SYNC_TO_VBLANK=0 vblank_mode=0
                    ./gradlew :fabric:1.21.5:runClientGameTest --stacktrace
                '''
            }
            post { always { archiveArtifacts artifacts: '**/run/clientGameTest/screenshots/*.png', allowEmptyArchive: true } }
        }

        // The verification harness lives in a separate private repo, deliberately: it is test
        // tooling, not part of the published mod. Cloned read-only with a deploy key scoped to that
        // one repo, so a compromised agent cannot push anywhere.
        stage('Fetch verification harness') {
            when { expression { return params.RUN_JAR_AUDIT || params.RUN_HUD_CHECK || params.RUN_CONFIG_CHECK || nightly() } }
            steps {
                // Jenkins verifies SSH host keys against the agent's known_hosts and refuses to
                // connect to a host it has never seen — "No ED25519 host key is known for
                // github.com". Seed it from GitHub's own published key list rather than weakening
                // verification or pinning keys that will eventually rotate. Runs on the agent,
                // because that is where the clone happens, and re-runs harmlessly.
                sh '''
                    set -e
                    mkdir -p ~/.ssh && chmod 700 ~/.ssh
                    touch ~/.ssh/known_hosts && chmod 600 ~/.ssh/known_hosts
                    if ! grep -q "^github.com " ~/.ssh/known_hosts 2>/dev/null; then
                        curl -sS https://api.github.com/meta \
                            | tr ',' '\n' \
                            | grep -oE '"(ssh-[a-z0-9]+|ecdsa-sha2-nistp256) [A-Za-z0-9+/=]+"' \
                            | tr -d '"' | sed 's/^/github.com /' >> ~/.ssh/known_hosts
                        echo "seeded known_hosts with GitHub's published host keys"
                    fi

                    # Report the agent's capabilities in one go. The harness needs all of these, and
                    # discovering them one failed build at a time is slow. Non-fatal on purpose: the
                    # point is a complete list, not the first missing item.
                    echo "--- agent capabilities"
                    echo "    whoami: $(whoami)   HOME: $HOME"
                    for tool in python3 java curl git Xvfb xdotool magick import convert ffmpeg; do
                        if command -v "$tool" >/dev/null 2>&1; then
                            echo "    ok      $tool"
                        else
                            echo "    MISSING $tool"
                        fi
                    done
                    echo "--- end capabilities"
                '''
                dir('verify') {
                    checkout([$class: 'GitSCM',
                        branches: [[name: '*/main']],
                        userRemoteConfigs: [[
                            url: 'git@github.com:SaolGhra/armor-hud-verify.git',
                            credentialsId: 'armor-hud-verify-key']]])
                }
                // Record which harness produced the results. A copied or stale harness is otherwise
                // invisible in the log, and its output looks exactly like a current one.
                sh 'cd verify && git rev-parse --short HEAD | sed "s/^/harness /"'
            }
        }

        // Static audit of every built jar: manifest shape, java target, guard branches, resource
        // paths, no test scaffolding. Seconds, no game launch — cheap enough for every build.
        stage('Audit jars') {
            when { expression { return params.RUN_JAR_AUDIT } }
            steps {
                sh '''
                    set -e
                    # Agents here are disposable containers, so python3 is installed per build rather
                    # than baked into an image or installed on the host — packages on the host are not
                    # in the container, which is why an earlier attempt at that changed nothing.
                    # Nothing persists: the container is discarded when the build ends.
                    if ! command -v python3 >/dev/null 2>&1; then
                        echo "installing python3 (absent from this agent image)"
                        if command -v apt-get >/dev/null 2>&1; then
                            apt-get update -qq && apt-get install -y -qq python3 >/dev/null
                        elif command -v apk >/dev/null 2>&1; then
                            apk add --no-cache python3 >/dev/null
                        elif command -v dnf >/dev/null 2>&1; then
                            dnf install -y -q python3 >/dev/null
                        fi
                        command -v python3 >/dev/null || {
                            echo "!! could not install python3 — the jar audit needs it"
                            echo "   bake it into the agent image, or drop RUN_JAR_AUDIT"
                            exit 1
                        }
                        echo "python3 $(python3 --version 2>&1 | cut -d' ' -f2) ready"
                    fi
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    # The harness is cloned beside the project, not inside it, so it cannot derive
                    # the project root from its own location.
                    export ARMOR_HUD_ROOT="$WORKSPACE"
                    python3 verify/audit_jars.py
                '''
            }
        }

        // In-world HUD assertions. Each node launches a real client twice (modded + no-mod baseline)
        // on a private Xvfb display under software GL, so this is slow — roughly 90 minutes for the
        // whole matrix. Off by default; run it nightly or before a release, not on every push.
        stage('HUD check') {
            when { expression { return params.RUN_HUD_CHECK || nightly() } }
            steps {
                sh '''
                    set -e
                    export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                    export PATH="$JAVA_HOME/bin:$PATH"
                    export ARMOR_HUD_HEADLESS=1
                    export ARMOR_HUD_ROOT="$WORKSPACE"

                    # This check launches a real client per node, so it needs a display server, an
                    # input tool and ImageMagick — none of which are in the stock agent image. Unlike
                    # python3 these are not worth installing per build: it is a GL stack plus mesa,
                    # and software rendering in a container makes an already slow check slower.
                    # Bake them into a custom agent image, or run this locally with
                    # ARMOR_HUD_HEADLESS=1, which is what it was built for.
                    missing=""
                    for tool in Xvfb xdotool import; do
                        command -v "$tool" >/dev/null 2>&1 || missing="$missing $tool"
                    done
                    if [ -n "$missing" ]; then
                        echo "!! this agent cannot run the in-game HUD check — missing:$missing"
                        echo "   it needs an image with xvfb, xdotool, imagemagick and mesa"
                        exit 1
                    fi

                    verify/hud_ingame.sh neoforge
                    verify/hud_ingame.sh fabric
                    verify/hud_ingame.sh forge
                '''
            }
            post { always { archiveArtifacts artifacts: 'build/hud-screenshots/*.png', allowEmptyArchive: true } }
        }

        stage('Collect jars') {
            steps {
                // chiseledBuild collects remapped jars into build/libs/<mod.version>/<loader>/.
                archiveArtifacts artifacts: 'build/libs/**/*.jar', fingerprint: true, excludes: '**/*-sources.jar'
            }
        }

        stage('Publish to Modrinth') {
            when { expression { return params.PUBLISH } }
            steps {
                withCredentials([string(credentialsId: 'modrinth-token', variable: 'MODRINTH_TOKEN')]) {
                    // The changelog goes through a file rather than straight onto the command line:
                    // release notes are multi-line and contain quotes and backticks, which would be
                    // mangled (or would break the shell) if interpolated into the sh string.
                    writeFile file: 'build/changelog.md', text: params.CHANGELOG ?: ''
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
            }
        }
    }

    post {
        always {
            script {
                def status = currentBuild.currentResult
                if (params.NOTIFY_URL?.trim()) {
                    sh """curl -sS -X POST -H 'Content-Type: application/json' \
                        -d '{"job":"${env.JOB_NAME}","build":${env.BUILD_NUMBER},"status":"${status}"}' \
                        '${params.NOTIFY_URL}' || true"""
                }
            }
            cleanWs()
        }
    }
}
