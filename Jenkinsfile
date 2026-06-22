def parsePropertiesFile(String content) {
    Map<String, String> properties = [:]
    content.readLines().each { line ->
        def trimmed = line.trim()

        if (!trimmed || trimmed.startsWith('#') || !line.contains('=')) {
            return
        }

        int separatorIndex = line.indexOf('=')
        properties[line.substring(0, separatorIndex).trim()] = line.substring(separatorIndex + 1).trim()
    }
    properties
}

def replacePropertyLine(String content, String key, String value) {
    if (value == null) {
        error("Refusing to update ${key} with a null value.")
    }

    def lines = content.readLines()
    def updatedLines = []
    boolean replaced = false

    lines.each { line ->
        def trimmed = line.trim()
        if (!trimmed.startsWith('#') && line.contains('=')) {
            int separatorIndex = line.indexOf('=')
            def existingKey = line.substring(0, separatorIndex).trim()
            if (existingKey == key) {
                if (!replaced) {
                    updatedLines << "${key}=${value}"
                    replaced = true
                }
                return
            }
        }

        updatedLines << line
    }

    if (!replaced) {
        updatedLines << "${key}=${value}"
    }

    return updatedLines.join('\n') + '\n'
}

def nextModVersion(String currentModVersion, String minecraftVersion) {
    if (!currentModVersion) {
        return minecraftVersion
    }

    int separatorIndex = currentModVersion.lastIndexOf('-')
    if (separatorIndex >= 0) {
        return currentModVersion.substring(0, separatorIndex + 1) + minecraftVersion
    }

    return "${currentModVersion}-${minecraftVersion}"
}

def shellQuote(String value) {
    return "'${value.replace("'", "'\"'\"'")}'"
}

def parseMavenVersions(String metadataXml) {
    def versions = []
    def matcher = metadataXml =~ /<version>([^<]+)<\/version>/
    matcher.each { match ->
        def value = (match[1] ?: '').trim()
        if (value) {
            versions << value
        }
    }
    versions
}

def parseMavenVersioningTag(String metadataXml, String tagName) {
    if (!metadataXml || !tagName) {
        return ''
    }

    def matcher = metadataXml =~ /<${tagName}>([^<]+)<\/${tagName}>/
    if (matcher.find()) {
        return (matcher.group(1) ?: '').trim()
    }
    return ''
}

def configureGradleRuntime(String javaHome, String projectCacheDir) {
    if (!javaHome) {
        error('configureGradleRuntime received an empty JAVA_HOME value.')
    }
    if (!projectCacheDir) {
        error('configureGradleRuntime received an empty project cache directory value.')
    }

    env.JAVA_HOME = javaHome
    env.PATH = "${env.JAVA_HOME}/bin:${env.PATH}"

    def existingGradleOpts = (env.GRADLE_OPTS ?: '')
        .replaceAll(/(^|\s)-Dorg\.gradle\.java\.home=\S+/, ' ')
        .replaceAll(/(^|\s)-Dorg\.gradle\.projectcachedir=\S+/, ' ')
        .trim()
    env.GRADLE_OPTS = "${existingGradleOpts} -Dorg.gradle.java.home=${env.JAVA_HOME} -Dorg.gradle.projectcachedir=${projectCacheDir}".trim()
}

pipeline {
    agent {
        label 'linux'
    }

    parameters {
        string(name: 'BRANCH', defaultValue: 'master', description: 'Git branch Jenkins should build and update from.')
        booleanParam(name: 'RUN_VERSION_UPDATE', defaultValue: false, description: 'Run a manual Minecraft dependency update before building.')
        string(name: 'TARGET_MINECRAFT_VERSION', defaultValue: '', description: 'Minecraft version to update to when RUN_VERSION_UPDATE is enabled (for example 26.2).')
        string(name: 'GITHUB_REPOSITORY', defaultValue: 'SaolGhra/Armor-Hud', description: 'owner/repo used for pushing update commits.')
        string(name: 'GITHUB_TOKEN_CREDENTIALS_ID', defaultValue: 'github-token', description: 'Jenkins credential ID containing a GitHub token with repo push scope.')
        string(name: 'NOTIFY_URL', defaultValue: 'https://notify.saolghra.co.uk/builds', description: 'Webhook endpoint used for build notifications.')
    }

    environment {
        GRADLE_USER_HOME = '/home/jenkins/.gradle'
        _JAVA_OPTIONS = '-Xmx2G -Xms512M'
    }

    options {
        timestamps()
        disableConcurrentBuilds()
        ansiColor('xterm')
        buildDiscarder(logRotator(daysToKeepStr: '365', numToKeepStr: '50'))
    }

    stages {
        stage('Preparation') {
            steps {
                script {
                    def detectedJavaHome = sh(
                        script: '''#!/bin/sh
set -eu
if [ -n "${JAVA_HOME:-}" ] && [ -x "${JAVA_HOME}/bin/java" ]; then
    printf '%s' "$JAVA_HOME"
    exit 0
fi
java_path=$(readlink -f "$(command -v java)")
printf '%s' "$(dirname "$(dirname "$java_path")")"
''',
                        returnStdout: true
                    ).trim()
                    env.GRADLE_PROJECT_CACHE_DIR = "${env.WORKSPACE}/.gradle-project-cache"
                    sh "mkdir -p ${shellQuote(env.GRADLE_PROJECT_CACHE_DIR)}"
                    configureGradleRuntime(detectedJavaHome, env.GRADLE_PROJECT_CACHE_DIR)
                    echo "Using JAVA_HOME ${env.JAVA_HOME} and project cache ${env.GRADLE_PROJECT_CACHE_DIR} for Gradle startup checks."
                }
                echo "Running on node: ${env.NODE_NAME}"
                sh 'chmod +x ./gradlew'
                sh 'java -version || true'
                sh './gradlew -version || gradle -version || true'
            }
        }

        stage('Resolve Update Target') {
            steps {
                script {
                    def requestedTargetMcVersion = (params.TARGET_MINECRAFT_VERSION ?: '').trim()
                    def shouldRunManualUpdate = params.RUN_VERSION_UPDATE || !!requestedTargetMcVersion

                    def propertiesContent = readFile('gradle.properties')
                    def properties = parsePropertiesFile(propertiesContent)
                    def currentMcVersion = properties.minecraft_version ?: ''
                    def currentModVersion = properties.mod_version ?: ''

                    if (!currentMcVersion || !currentModVersion) {
                        error('gradle.properties is missing minecraft_version or mod_version.')
                    }

                    if (!shouldRunManualUpdate) {
                        writeFile file: '.jenkins-release.properties', text: [
                            mode: 'build-only',
                            current_mc_version: currentMcVersion,
                            target_mc_version: currentMcVersion,
                            current_mod_version: currentModVersion,
                            target_mod_version: currentModVersion,
                            target_branch: params.BRANCH,
                            target_loader_version: properties.loader_version ?: '',
                            target_fabric_version: properties.fabric_version ?: '',
                            target_modmenu_version: properties.modmenu_version ?: '',
                            target_cloth_config_version: properties.cloth_config_version ?: '',
                            target_yarn_mappings: properties.yarn_mappings ?: ''
                        ].collect { key, value -> "${key}=${value}" }.join('\n') + '\n'

                        currentBuild.description = "Build only (Minecraft ${currentMcVersion})"
                        echo 'RUN_VERSION_UPDATE disabled; building current branch state without dependency updates.'
                        return
                    }

                    if (!params.RUN_VERSION_UPDATE && requestedTargetMcVersion) {
                        echo 'TARGET_MINECRAFT_VERSION was provided, so manual update mode is enabled for this run.'
                    }

                    def targetMcVersion = requestedTargetMcVersion
                    if (!targetMcVersion) {
                        error('TARGET_MINECRAFT_VERSION is required when RUN_VERSION_UPDATE is enabled.')
                    }

                    def latestLoader = sh(
                        script: '''#!/bin/sh
set -eu
curl -fsSL https://meta.fabricmc.net/v2/versions/loader |
tr -d '[:space:]' |
grep -o '"version":"[^"]*","stable":true' |
sed 's/"version":"//;s/","stable":true//' |
head -n 1
''',
                        returnStdout: true
                    ).trim()
                    if (!latestLoader) {
                        error('Unable to determine the latest Fabric loader version from Fabric metadata.')
                    }

                    def latestYarnMappings = sh(
                        script: '''#!/bin/sh
set -eu
target_version=''' + shellQuote(targetMcVersion) + '''
latest_yarn=$(curl -fsSL "https://meta.fabricmc.net/v2/versions/yarn/${target_version}" |
    tr -d '[:space:]' |
    grep -o '"version":"[^"]*","stable":true' |
    sed 's/"version":"//;s/","stable":true//' |
    head -n 1 || true)
if [ -z "$latest_yarn" ]; then
    latest_yarn=$(curl -fsSL "https://meta.fabricmc.net/v2/versions/yarn/${target_version}" |
        tr -d '[:space:]' |
        grep -o '"version":"[^"]*"' |
        head -n 1 |
        sed 's/"version":"//;s/"$//' |
        grep -v '^$' || true)
fi
printf '%s' "$latest_yarn"
''',
                        returnStdout: true
                    ).trim()

                    def targetMappingsChannel = latestYarnMappings ? 'yarn' : 'none'
                    if (!latestYarnMappings) {
                        echo "No Yarn mappings found for Minecraft ${targetMcVersion}; using non-obfuscated mappings mode."
                    }

                    def latestFabricApi = sh(
                        script: '''#!/bin/sh
set -eu
target_version=''' + shellQuote(targetMcVersion) + '''
curl -fsSL https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml |
tr -d '[:space:]' |
grep -o '<version>[^<]*</version>' |
grep -F "+${target_version}</version>" |
sed 's#.*<version>##' |
sed 's#</version>.*##' |
tail -n 1
''',
                        returnStdout: true
                    ).trim()
                    if (!latestFabricApi) {
                        error("No Fabric API version published yet for Minecraft ${targetMcVersion}.")
                    }

                    def modMenuMetadata = sh(
                        script: '''#!/bin/sh
set -eu
curl -fsSL https://maven.terraformersmc.com/com/terraformersmc/modmenu/maven-metadata.xml
''',
                        returnStdout: true
                    )
                    def modMenuRelease = parseMavenVersioningTag(modMenuMetadata, 'release')
                    def modMenuLatest = parseMavenVersioningTag(modMenuMetadata, 'latest')
                    def modMenuVersions = parseMavenVersions(modMenuMetadata)
                    def latestModMenu = modMenuRelease ?: modMenuLatest ?: (modMenuVersions ? modMenuVersions.first() : '')
                    if (!latestModMenu) {
                        error('Unable to determine the latest Mod Menu version from Terraformers Maven metadata.')
                    }

                    def clothMetadata = sh(
                        script: '''#!/bin/sh
set -eu
curl -fsSL https://maven.shedaniel.me/me/shedaniel/cloth/cloth-config-fabric/maven-metadata.xml
''',
                        returnStdout: true
                    )
                    def clothRelease = parseMavenVersioningTag(clothMetadata, 'release')
                    def clothLatest = parseMavenVersioningTag(clothMetadata, 'latest')
                    def clothVersions = parseMavenVersions(clothMetadata)
                    if (!clothVersions) {
                        error('Unable to determine Cloth Config versions from Shedaniel Maven metadata.')
                    }
                    def exactClothPrefix = "${targetMcVersion}."
                    def fallbackVersionKey = targetMcVersion.tokenize('.').take(2).join('.')
                    def fallbackClothPrefix = fallbackVersionKey ? "${fallbackVersionKey}." : exactClothPrefix
                    def latestClothConfig = clothVersions.findAll { it.startsWith(exactClothPrefix) }.with { it ? it.first() : null }
                    if (!latestClothConfig && fallbackClothPrefix != exactClothPrefix) {
                        latestClothConfig = clothVersions.findAll { it.startsWith(fallbackClothPrefix) }.with { it ? it.first() : null }
                    }
                    if (!latestClothConfig) {
                        latestClothConfig = clothRelease ?: clothLatest ?: clothVersions.first()
                        echo "No Cloth Config version prefix-matching ${targetMcVersion}; using latest available ${latestClothConfig}."
                    }

                    def targetLoaderVersion = latestLoader
                    def targetFabricVersion = latestFabricApi
                    def targetModMenuVersion = latestModMenu
                    def targetClothConfigVersion = latestClothConfig
                    def targetYarnMappings = latestYarnMappings
                    def targetModVersion = nextModVersion(currentModVersion, targetMcVersion)
                    def targetBranch = targetMcVersion

                    def updatedProperties = propertiesContent
                    updatedProperties = replacePropertyLine(updatedProperties, 'minecraft_version', targetMcVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'mappings_channel', targetMappingsChannel)
                    if (targetMappingsChannel == 'yarn') {
                        updatedProperties = replacePropertyLine(updatedProperties, 'yarn_mappings', targetYarnMappings)
                    }
                    updatedProperties = replacePropertyLine(updatedProperties, 'loader_version', targetLoaderVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'fabric_version', targetFabricVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'modmenu_version', targetModMenuVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'cloth_config_version', targetClothConfigVersion)
                    updatedProperties = replacePropertyLine(updatedProperties, 'mod_version', targetModVersion)
                    writeFile file: 'gradle.properties', text: updatedProperties

                    writeFile file: '.jenkins-release.properties', text: [
                        mode: 'manual-update',
                        current_mc_version: currentMcVersion,
                        target_mc_version: targetMcVersion,
                        current_mod_version: currentModVersion,
                        target_mod_version: targetModVersion,
                        target_branch: targetBranch,
                        target_mappings_channel: targetMappingsChannel,
                        target_loader_version: targetLoaderVersion,
                        target_fabric_version: targetFabricVersion,
                        target_modmenu_version: targetModMenuVersion,
                        target_cloth_config_version: targetClothConfigVersion,
                        target_yarn_mappings: targetYarnMappings
                    ].collect { key, value -> "${key}=${value}" }.join('\n') + '\n'

                    currentBuild.description = "Manual update ${currentMcVersion} -> ${targetMcVersion} (${targetBranch})"
                    def mappingsLabel = targetMappingsChannel == 'yarn' ? "Yarn ${targetYarnMappings}" : 'non-obfuscated mappings mode'
                    echo "Prepared manual update ${currentMcVersion} -> ${targetMcVersion} for branch ${targetBranch} using ${mappingsLabel}, loader ${targetLoaderVersion}, Fabric API ${targetFabricVersion}, Mod Menu ${targetModMenuVersion}, and Cloth Config ${targetClothConfigVersion}."
                }
            }
        }

        stage('Build') {
            steps {
                script {
                    def releaseMetadata = parsePropertiesFile(readFile('.jenkins-release.properties'))
                    def currentProperties = parsePropertiesFile(readFile('gradle.properties'))

                    def mappingsChannel = (currentProperties.mappings_channel ?: '').trim().toLowerCase()
                    if (mappingsChannel == 'none') {
                        echo 'Detected non-obfuscated Minecraft mappings mode; ensuring Temurin JDK 25 is available for Gradle toolchain requirements.'
                        sh '''#!/bin/sh
set -eu
JDK_DIR="$WORKSPACE/.jdk/temurin-25"

if [ ! -x "$JDK_DIR/bin/java" ]; then
    mkdir -p "$WORKSPACE/.jdk"
    cd "$WORKSPACE/.jdk"

    ASSET_URL=$(curl -fsSL "https://api.adoptium.net/v3/assets/latest/25/hotspot?architecture=x64&heap_size=normal&image_type=jdk&jvm_impl=hotspot&os=linux&vendor=eclipse" |
        grep -m1 -o 'https://[^" ]*tar.gz')

    if [ -z "$ASSET_URL" ]; then
        echo "Failed to resolve a Temurin 25 download URL from Adoptium API" >&2
        exit 1
    fi

    curl -fsSL "$ASSET_URL" -o temurin-25.tar.gz
    rm -rf temurin-25-extract "$JDK_DIR"
    mkdir -p temurin-25-extract
    tar -xzf temurin-25.tar.gz -C temurin-25-extract

    EXTRACTED_DIR=$(find temurin-25-extract -mindepth 1 -maxdepth 1 -type d | head -n 1)
    if [ -z "$EXTRACTED_DIR" ]; then
        echo "Failed to extract Temurin 25 archive" >&2
        exit 1
    fi

    mv "$EXTRACTED_DIR" "$JDK_DIR"
fi
'''
                        def projectCacheDir = env.GRADLE_PROJECT_CACHE_DIR ?: "${env.WORKSPACE}/.gradle-project-cache"
                        sh "mkdir -p ${shellQuote(projectCacheDir)}"
                        configureGradleRuntime("${env.WORKSPACE}/.jdk/temurin-25", projectCacheDir)
                        sh 'java -version'
                    }

                    echo "Building Armor HUD branch ${params.BRANCH} for Minecraft ${releaseMetadata.target_mc_version ?: releaseMetadata.current_mc_version}..."
                    sh './gradlew clean build -x test'
                }
            }
        }

        stage('Archive') {
            steps {
                echo "Archiving built JARs for branch ${params.BRANCH}..."
                archiveArtifacts artifacts: 'build/libs/*.jar', fingerprint: true
            }
        }
    }

    post {
        always {
            script {
                def releaseMetadata = fileExists('.jenkins-release.properties') ? parsePropertiesFile(readFile('.jenkins-release.properties')) : [:]

                def requestedTargetMcVersion = (params.TARGET_MINECRAFT_VERSION ?: '').trim()
                def shouldRunManualUpdate = params.RUN_VERSION_UPDATE || !!requestedTargetMcVersion

                if (shouldRunManualUpdate) {
                    try {
                        String githubToken = null
                        try {
                            withCredentials([string(credentialsId: params.GITHUB_TOKEN_CREDENTIALS_ID, variable: 'GITHUB_TOKEN')]) {
                                githubToken = env.GITHUB_TOKEN
                            }
                        } catch (Exception ignored) {
                            echo "Credential ${params.GITHUB_TOKEN_CREDENTIALS_ID} is not Secret Text. Trying Username/Password credentials."
                        }

                        if (!githubToken) {
                            withCredentials([usernamePassword(credentialsId: params.GITHUB_TOKEN_CREDENTIALS_ID, usernameVariable: 'GITHUB_USERNAME', passwordVariable: 'GITHUB_TOKEN')]) {
                                githubToken = env.GITHUB_TOKEN
                            }
                        }

                        if (!githubToken) {
                            error("Unable to resolve a GitHub token from credentials ${params.GITHUB_TOKEN_CREDENTIALS_ID}.")
                        }

                        sh 'git config user.name "jenkins"'
                        sh 'git config user.email "jenkins@localhost"'
                        sh '''#!/bin/sh
set -eu
mkdir -p .git/info
touch .git/info/exclude
for pattern in '.jdk/' '.gradle-project-cache/' '.jenkins-release.properties'; do
    if ! grep -qxF "$pattern" .git/info/exclude; then
        printf '%s\n' "$pattern" >> .git/info/exclude
    fi
done
git checkout -- gradlew || true
git add -A
git reset -q -- .jdk .gradle-project-cache .jenkins-release.properties gradlew || true
'''

                        def hasStagedChanges = sh(script: 'git diff --cached --quiet', returnStatus: true) != 0
                        if (hasStagedChanges) {
                            def targetMcVersion = releaseMetadata.target_mc_version ?: params.TARGET_MINECRAFT_VERSION
                            def targetBranch = releaseMetadata.target_branch ?: targetMcVersion
                            if (!targetBranch) {
                                error('Unable to determine the target branch for the manual update push.')
                            }

                            def commitMessage = "chore: update Minecraft to ${targetMcVersion}"
                            sh "git commit -m ${shellQuote(commitMessage)}"

                            def remoteUrl = "https://x-access-token:${githubToken}@github.com/${params.GITHUB_REPOSITORY}.git"
                            def qualifiedTargetBranch = targetBranch.startsWith('refs/heads/') ? targetBranch : "refs/heads/${targetBranch}"
                            def branchRefSpec = "HEAD:${qualifiedTargetBranch}"
                            sh "git remote set-url origin ${shellQuote(remoteUrl)}"
                            sh "git push origin ${shellQuote(branchRefSpec)}"

                            echo "Pushed manual update commit to ${params.GITHUB_REPOSITORY} (${targetBranch})."
                        } else {
                            echo 'RUN_VERSION_UPDATE was enabled but no file changes were produced; nothing to push.'
                        }
                    } catch (Exception pushError) {
                        currentBuild.result = 'FAILURE'
                        echo "Failed to push manual update changes: ${pushError.getMessage()}"
                    }
                }

                def finalResult = currentBuild.currentResult ?: 'SUCCESS'
                def attemptedVersion = releaseMetadata.target_mc_version ?: releaseMetadata.current_mc_version ?: params.TARGET_MINECRAFT_VERSION ?: 'unknown'
                def modeLabel = shouldRunManualUpdate ? 'manual update' : 'build-only run'

                if (finalResult == 'SUCCESS') {
                    echo "SUCCESS ${modeLabel} completed successfully."
                } else {
                    echo "FAILURE ${modeLabel} failed. Check console output for details."
                }

                def message = finalResult == 'SUCCESS'
                    ? "SUCCESS Jenkins ${modeLabel} succeeded for ${env.JOB_NAME} #${env.BUILD_NUMBER} (Minecraft ${attemptedVersion}). ${env.BUILD_URL}"
                    : "FAILURE Jenkins ${modeLabel} failed for ${env.JOB_NAME} #${env.BUILD_NUMBER} while targeting Minecraft ${attemptedVersion}. ${env.BUILD_URL}"
                sh "curl -fsSL --retry 3 -X POST --data-binary ${shellQuote(message)} ${shellQuote(params.NOTIFY_URL)}"

                cleanWs()
            }
        }
    }
}