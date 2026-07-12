// Armor HUD CI — builds the whole Stonecutter/Architectury matrix and (optionally) publishes to
// Modrinth. Replaces the old single-version "bump gradle.properties" pipeline: with the monorepo,
// every supported Minecraft version is a Gradle node, so CI just iterates the matrix.
//
// See the project docs for why the toolchain is pinned the way it is. 26.x is not in the
// matrix yet (unobfuscated — see the project docs).

pipeline {
    agent { label 'linux' }

    parameters {
        booleanParam(name: 'PUBLISH', defaultValue: false,
                description: 'Publish the built jars to Modrinth (needs the modrinth-token credential).')
        booleanParam(name: 'RUN_SCREENSHOT_TESTS', defaultValue: false,
                description: 'Run the Fabric Client GameTest screenshot tests (needs a display / Xvfb).')
        string(name: 'ONLY_VERSION', defaultValue: '',
                description: 'Optional: build just this MC version (e.g. 1.21.5) instead of the whole matrix.')
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
        // The matrix. Add versions here as they are validated (see the project docs).
        FABRIC_VERSIONS   = '1.20.1 1.21.1 1.21.5 1.21.11'
        NEOFORGE_VERSIONS = '1.21.1 1.21.5 1.21.11'
        FORGE_VERSIONS    = ''            // 1.20.1 once the forge module lands
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

                    build_node() {  # $1 = loader, $2 = mc
                        echo "== building :$1:$2 =="
                        ./gradlew ":$1:$2:build" -x runGameTest -x runClientGameTest --stacktrace
                    }

                    for v in ${ONLY_VERSION:-$FABRIC_VERSIONS};   do case " $FABRIC_VERSIONS " in *" $v "*) build_node fabric "$v";; esac; done
                    for v in ${ONLY_VERSION:-$NEOFORGE_VERSIONS}; do case " $NEOFORGE_VERSIONS " in *" $v "*) build_node neoforge "$v";; esac; done
                    for v in ${ONLY_VERSION:-$FORGE_VERSIONS};    do case " $FORGE_VERSIONS " in *" $v "*) build_node forge "$v";; esac; done
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
                sh '''
                    set -e
                    mkdir -p dist
                    find . -path '*/build/libs/*.jar' ! -name '*-dev*.jar' ! -name '*-sources.jar' -exec cp {} dist/ \\;
                    ls -la dist/
                '''
                archiveArtifacts artifacts: 'dist/*.jar', fingerprint: true
            }
        }

        stage('Publish to Modrinth') {
            when { expression { return params.PUBLISH } }
            steps {
                withCredentials([string(credentialsId: 'modrinth-token', variable: 'MODRINTH_TOKEN')]) {
                    sh '''
                        set -e
                        export JAVA_HOME="$WORKSPACE/.jdk/temurin-21"
                        export PATH="$JAVA_HOME/bin:$PATH"
                        publish_node() { echo "== publishing :$1:$2 =="; ./gradlew ":$1:$2:publishModrinth" --stacktrace; }
                        for v in ${ONLY_VERSION:-$FABRIC_VERSIONS};   do case " $FABRIC_VERSIONS " in *" $v "*) publish_node fabric "$v";; esac; done
                        for v in ${ONLY_VERSION:-$NEOFORGE_VERSIONS}; do case " $NEOFORGE_VERSIONS " in *" $v "*) publish_node neoforge "$v";; esac; done
                        for v in ${ONLY_VERSION:-$FORGE_VERSIONS};    do case " $FORGE_VERSIONS " in *" $v "*) publish_node forge "$v";; esac; done
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
