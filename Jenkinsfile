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
        string(name: 'NOTIFY_URL', defaultValue: 'https://notify.saolghra.co.uk/builds',
                description: 'Webhook pinged on success/failure.')
    }

    options {
        timestamps()
        ansiColor('xterm')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '50', daysToKeepStr: '365'))
    }

    environment {
        GRADLE_USER_HOME = "${WORKSPACE}/.gradle"
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
