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

// The authoritative "<loader>: [<mc>, ...]" map, pulled from the Stonecutter tree itself via the
// `printNodes` Gradle task rather than hand-copied here. A hand-copied list silently drifts the
// moment a version is added to or removed from settings.gradle.kts — this replaced exactly such a
// list, which had gone stale and was missing every 26.x node.
def loadNodeMap() {
    def out = sh(script: '''
        set -e
        export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
        export PATH="$JAVA_HOME/bin:$PATH"
        ./gradlew printNodes -q
    ''', returnStdout: true).trim()
    def map = [:]
    out.readLines().each { line ->
        def parts = line.trim().split(/\s+/)
        if (parts.size() != 2) { return } // e.g. Stonecutter's own "Running Stonecutter 0.6" banner
        map.computeIfAbsent(parts[0]) { [] } << parts[1]
    }
    return map
}

// Expands a ";"-separated "<loader> <mc>" list (bare "<loader>" means every version of it) against
// the given node map. Empty/blank raw means "everything" — used by both HUD_NODES and PUBLISH_NODES.
def expandNodes(String raw, Map allVersions) {
    def entries = raw?.trim() ? (raw.split(';') as List) : (allVersions.keySet() as List)
    def nodes = []
    entries.each { rawEntry ->
        def entry = rawEntry.trim()
        if (!entry) { return }
        def parts = entry.split(/\s+/)
        if (parts.size() == 1) {
            (allVersions[parts[0]] ?: []).each { v -> nodes << ("${parts[0]} ${v}" as String) }
        } else {
            nodes << entry
        }
    }
    return nodes
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
                description: 'Which node(s) DIAGNOSE_HUD runs, as a ";"-separated list of "<loader> <mc>".')
        string(name: 'HUD_NODES', defaultValue: '',
                description: 'Restrict RUN_HUD_CHECK to a ";"-separated list of "<loader> <mc>" (or ' +
                        'bare "<loader>" for all its versions). Empty = the whole matrix. Lets a long ' +
                        'run be split into agent-sized batches.')
        string(name: 'PUBLISH_NODES', defaultValue: '',
                description: 'Restrict PUBLISH_GITHUB/PUBLISH_MODRINTH to a ";"-separated list of ' +
                        '"<loader> <mc>" (or bare "<loader>" for all its versions), same format as ' +
                        'HUD_NODES. Empty = the whole matrix — use that for a release where every ' +
                        'node is genuinely new/changed; set this for a release that only adds or ' +
                        'fixes specific nodes, so the rest keep their existing Modrinth entries ' +
                        'instead of getting a redundant unchanged one.')

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
            // Only build the whole matrix when something downstream consumes the jars: the jar audit,
            // or a publish. The HUD check does NOT need it — hud_ingame.sh sets each node active and
            // builds it via runClient — so a HUD-check-only run (e.g. a batched HUD_NODES run) must
            // not pay for a full chiseledBuild first, which is itself long and agent-risky.
            when { expression { return !params.DIAGNOSE_HUD &&
                    (params.RUN_JAR_AUDIT || params.PUBLISH_GITHUB || params.PUBLISH_MODRINTH) } }
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

        // Rewritten to fan out across several agents instead of one. Splitting an 18+ node HUD check
        // into N parallel branches — each provisioning its OWN Docker container from the cloud (cap
        // 100, so this is nowhere near the ceiling) — turns an N x per-node-time serial run into
        // roughly per-node-time x (N/batchCount). This only helps the CI side; a Fabric-only local
        // run (see the agent notes) is optimized separately.
        stage('HUD check') {
            when { expression { return params.RUN_HUD_CHECK && !params.DIAGNOSE_HUD } }
            steps {
                script {
                    def nodes = expandNodes(params.HUD_NODES, loadNodeMap())

                    // Capped at 2: the CI host also runs the user's other live production services
                    // (not dedicated CI capacity). 4 concurrent llvmpipe (software GL) Minecraft
                    // clients previously starved the kernel's own workqueues and froze the whole box
                    // solid (no OOM — pure CPU/scheduler exhaustion, confirmed via journalctl after
                    // a hard reboot). LP_NUM_THREADS below is the second layer of defense.
                    int batchCount = Math.max(1, Math.min(2, nodes.size()))
                    def batches = (0..<batchCount).collect { [] as List }
                    nodes.eachWithIndex { n, i -> batches[i % batchCount] << n }

                    notify("HUD check started", "${nodes.size()} nodes across ${batchCount} parallel agents — software GL", 'hourglass')

                    def branches = [:]
                    batches.eachWithIndex { batchNodes, i ->
                        if (batchNodes.isEmpty()) return
                        def batchName = "batch-${i}"
                        def hudNodesForBatch = batchNodes.join(';')
                        branches[batchName] = {
                            node('linux') {
                                checkout scm
                                sh '''
                                    set -e
                                    JDK_DIR="$WORKSPACE/.jdk/temurin-21"
                                    if [ ! -x "$JDK_DIR"/bin/javac ]; then
                                        mkdir -p "$JDK_DIR"
                                        curl -sSL "https://api.adoptium.net/v3/binary/latest/21/ga/linux/x64/jdk/hotspot/normal/eclipse" \
                                            -o "$WORKSPACE/.jdk/jdk21.tar.gz"
                                        tar -xzf "$WORKSPACE/.jdk/jdk21.tar.gz" -C "$JDK_DIR" --strip-components=1
                                        rm -f "$WORKSPACE/.jdk/jdk21.tar.gz"
                                    fi
                                    chmod +x gradlew
                                '''
                                sh '''
                                    set -e
                                    mkdir -p ~/.ssh && chmod 700 ~/.ssh
                                    touch ~/.ssh/known_hosts && chmod 600 ~/.ssh/known_hosts
                                    if ! grep -q "^github.com " ~/.ssh/known_hosts 2>/dev/null; then
                                        curl -sS https://api.github.com/meta \
                                            | tr ',' '\\n' \
                                            | grep -oE '"(ssh-[a-z0-9]+|ecdsa-sha2-nistp256) [A-Za-z0-9+/=]+"' \
                                            | tr -d '"' | sed 's/^/github.com /' >> ~/.ssh/known_hosts
                                    fi
                                '''
                                dir('verify') {
                                    checkout([$class: 'GitSCM',
                                        branches: [[name: '*/main']],
                                        userRemoteConfigs: [[
                                            url: 'git@github.com:SaolGhra/armor-hud-verify.git',
                                            credentialsId: 'armor-hud-verify-key']]])
                                }
                                withEnv(["HUD_NODES=${hudNodesForBatch}"]) {
                                    sh '''
                                        set -e
                                        set -o pipefail
                                        export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                                        export PATH="$JAVA_HOME/bin:$PATH"
                                        export ARMOR_HUD_HEADLESS=1
                                        export ARMOR_HUD_ROOT="$WORKSPACE"
                                        # llvmpipe (software GL) defaults to one rasterizer thread per
                                        # core; unbounded, N concurrent clients contend for N x nproc
                                        # threads and can starve the host. Cap per-client threads so
                                        # total contention stays bounded regardless of batch count.
                                        export LP_NUM_THREADS=2

                                        if ! command -v Xvfb >/dev/null 2>&1 || ! command -v python3 >/dev/null 2>&1; then
                                            apt-get update -qq
                                            apt-get install -y -qq \
                                                python3 xvfb xdotool openbox imagemagick \
                                                libgl1-mesa-dri libglu1-mesa mesa-utils \
                                                libxext6 libxrender1 libxtst6 libxi6 libxrandr2 \
                                                libxcursor1 libxinerama1 libxxf86vm1 >/dev/null
                                        fi
                                        for tool in Xvfb xdotool import python3; do
                                            command -v "$tool" >/dev/null 2>&1 || {
                                                echo "!! $tool still missing after install"; exit 1; }
                                        done

                                        mkdir -p build
                                        : > build/hud-check.txt
                                        echo "HUD check batch restricted to: $HUD_NODES"
                                        OLDIFS=$IFS; IFS=';'
                                        for NODE in $HUD_NODES; do
                                            IFS=$OLDIFS
                                            [ -n "$NODE" ] || continue
                                            verify/hud_ingame.sh $NODE 2>&1 | tee -a build/hud-check.txt || true
                                        done
                                        IFS=$OLDIFS
                                    '''
                                }
                                archiveArtifacts artifacts: 'build/hud-check.txt,build/hud-screenshots/*.png,build/hud-screenshots/*.log',
                                                 allowEmptyArchive: true
                                stash name: "hud-${batchName}", includes: 'build/hud-check.txt'
                            }
                        }
                    }

                    parallel branches

                    sh 'mkdir -p build/hud-agg'
                    branches.keySet().each { batchName ->
                        try {
                            unstash "hud-${batchName}"
                            sh "cp build/hud-check.txt build/hud-agg/hud-check-${batchName}.txt"
                        } catch (err) {
                            echo "no results stashed for ${batchName}: ${err}"
                        }
                    }
                    sh 'cat build/hud-agg/*.txt > build/hud-check.txt 2>/dev/null || true'

                    def pass = sh(script: "grep -c '  PASS ' build/hud-check.txt || true", returnStdout: true).trim()
                    def fail = sh(script: "grep -c '  FAIL ' build/hud-check.txt || true", returnStdout: true).trim()
                    notify("HUD check: ${pass} passed, ${fail} failed", "build #${env.BUILD_NUMBER}",
                           fail == '0' ? 'white_check_mark' : 'rotating_light',
                           fail == '0' ? 'default' : 'high')
                    archiveArtifacts artifacts: 'build/hud-check.txt', allowEmptyArchive: true
                    // A FAIL anywhere must fail the build — otherwise a false green ships a broken HUD.
                    if (fail != '0') {
                        error("HUD check: ${fail} assertion failure(s) — see build/hud-check.txt")
                    }
                    if (pass == '0') {
                        error("HUD check produced no PASS lines — harness or launch problem, not a clean run")
                    }
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

                    # DIAGNOSE_NODE is one or more "<loader> <mc>" pairs separated by ';', so a single
                    # run can shake out several risk classes at once (Fabric refmap, old render
                    # pipeline, Forge) instead of babysitting one build per node.
                    export ARMOR_HUD_HEADLESS=1 SETTLE="${SETTLE:-60}"
                    DIAG_FAIL=0
                    OLDIFS=$IFS; IFS=';'
                    for NODE in $DIAGNOSE_NODE; do
                        IFS=$OLDIFS
                        set -- $NODE
                        LOADER="$1"; MC="$2"
                        [ -n "$LOADER" ] && [ -n "$MC" ] || { echo "!! skipping malformed node '$NODE'"; continue; }
                        echo "############################################################"
                        echo "### DIAGNOSE NODE: $LOADER $MC"
                        echo "############################################################"

                        # hud_ingame.sh sets the node active and runs its client, so chiseledBuild is
                        # unnecessary here. -Parmorhud.verify=true on the set-active is what compiles
                        # ArmorHudVerifyHook in — the const resolves at set-active, not at runClient —
                        # so match it here and the pre-build is reused rather than rebuilt.
                        echo "=== building $LOADER:$MC only (with the verify hook compiled in)"
                        ./gradlew --console=plain -q -Parmorhud.verify=true "Set active project to $MC"
                        ./gradlew -Parmorhud.verify=true ":$LOADER:$MC:build" -x test --stacktrace

                        echo "=== running one node: $LOADER $MC"
                        # Timeline of whether Minecraft's main window ever appears (the harness picks
                        # its display dynamically from :77+). Fully guarded for set -e.
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

                        # Describe the capture in the console — "is the frame black, a loading screen,
                        # or the world with a HUD?" is the whole question, and artifacts have repeatedly
                        # not been there when needed.
                        echo "=== capture stats: $LOADER $MC"
                        SHOT="build/hud-screenshots/$LOADER-$MC-hud.png"
                        if [ -f "$SHOT" ]; then
                            magick identify "$SHOT" 2>/dev/null || identify "$SHOT" 2>/dev/null
                            echo "    mean brightness (0=black, 65535=white):"
                            magick "$SHOT" -format "      %[mean]" info: 2>/dev/null && echo
                            echo "    distinct colours (a loading screen has very few):"
                            magick "$SHOT" -format "      %k" info: 2>/dev/null && echo
                            # Downscaled base64 so the actual image is visible from the console. Decode:
                            #   grep 'B64IMG <loader>-<mc>' log | awk '{print $3}' | base64 -d > x.png
                            magick "$SHOT" -resize 480x /tmp/diag_small.png 2>/dev/null
                            echo "B64IMG $LOADER-$MC $(base64 -w0 /tmp/diag_small.png 2>/dev/null)"
                        else
                            echo "    (no capture written)"
                            DIAG_FAIL=1
                        fi

                        echo "=== verify-hook lines from the client log: $LOADER $MC"
                        grep -F "[armor_hud verify]" build/hud-screenshots/$LOADER-$MC.log 2>/dev/null || echo "(hook printed nothing — it never fired)"

                        echo "=== client log tail: $LOADER $MC"
                        tail -40 build/hud-screenshots/$LOADER-$MC.log 2>/dev/null || echo "(no client log)"
                    done
                    IFS=$OLDIFS

                    echo "=== any PNG screenshots anywhere in the workspace"
                    find "$WORKSPACE" -name "*.png" -path "*screenshots*" 2>/dev/null | head -20 || true

                    # A diagnose that produced no capture for some node is a failure worth surfacing in
                    # red, even though each node's own crash is tolerated (|| true) so the others run.
                    [ "$DIAG_FAIL" = 0 ] || { echo "!! at least one node produced no capture"; exit 1; }
                '''
            }
            post {
                always {
                    archiveArtifacts artifacts: 'build/hud-screenshots/*', allowEmptyArchive: true
                }
            }
        }

        stage('Collect jars') {
            // Same gate as Build matrix: without it build/libs is empty and archiveArtifacts fails.
            when { expression { return !params.DIAGNOSE_HUD &&
                    (params.RUN_JAR_AUDIT || params.PUBLISH_GITHUB || params.PUBLISH_MODRINTH) } }
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
                script {
                    // Empty PUBLISH_NODES (the common "everything is genuinely new" release) attaches
                    // every built jar, same as before this existed. A restricted list only attaches
                    // the matching jars — see PUBLISH_NODES' own description for why.
                    env.PUBLISH_NODE_FILTER = params.PUBLISH_NODES?.trim() ?
                            expandNodes(params.PUBLISH_NODES, loadNodeMap()).join(';') : ''
                }
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

                        # Attach every built jar, sources excluded — or, if PUBLISH_NODE_FILTER is
                        # set, only the jars for those specific "<loader> <mc>" nodes. Jar names are
                        # armor_hud-<loader>-<VERSION>+<mc>.jar, and VERSION is already known exactly,
                        # so it splits the name cleanly without guessing at a regex for it.
                        COUNT=0
                        for jar in build/libs/*/*.jar; do
                            case "$jar" in *-sources.jar) continue;; esac
                            name=$(basename "$jar")
                            if [ -n "$PUBLISH_NODE_FILTER" ]; then
                                rest="${name#armor_hud-}"; rest="${rest%.jar}"
                                loader="${rest%%-"$VERSION"+*}"
                                mc="${rest##*-"$VERSION"+}"
                                case ";$PUBLISH_NODE_FILTER;" in
                                    *";$loader $mc;"*) ;;
                                    *) continue ;;
                                esac
                            fi
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
                script {
                    // Empty PUBLISH_NODES publishes every node, same as before this existed. See
                    // PUBLISH_NODES' own description, and chiseledPublish's publish.nodes property.
                    env.PUBLISH_NODE_FILTER = params.PUBLISH_NODES?.trim() ?
                            expandNodes(params.PUBLISH_NODES, loadNodeMap()).join(';') : ''
                }
                withCredentials([string(credentialsId: 'modrinth-token', variable: 'MODRINTH_TOKEN')]) {
                    sh '''
                        set -e
                        export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                        export PATH="$JAVA_HOME/bin:$PATH"
                        NODES_ARG=""
                        [ -n "$PUBLISH_NODE_FILTER" ] && NODES_ARG="-Ppublish.nodes=$PUBLISH_NODE_FILTER"
                        # chiseledPublish runs publishMods for every version (each with its source
                        # active) — or only the nodes NODES_ARG restricts it to.
                        if [ -s build/changelog.md ]; then
                            ./gradlew chiseledPublish $NODES_ARG -Pchangelog="$(cat build/changelog.md)" --stacktrace
                        else
                            ./gradlew chiseledPublish $NODES_ARG --stacktrace
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
