@Library('gbif-common-jenkins-pipelines') _

pipeline {
  agent any
  tools {
    maven 'Maven 3.9.9'
    jdk 'OpenJDK17'
  }
  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
    skipStagesAfterUnstable()
    timestamps()
  }
  triggers {
    snapshotDependencies()
  }
  parameters {
    separator(name: "release_separator", sectionHeader: "Release Main Project Parameters")
    booleanParam(name: 'RELEASE', defaultValue: false, description: 'Do a Maven release')
    string(name: 'RELEASE_VERSION', defaultValue: '', description: 'Release version (optional)')
    string(name: 'DEVELOPMENT_VERSION', defaultValue: '', description: 'Development version (optional)')
    booleanParam(name: 'DRY_RUN_RELEASE', defaultValue: false, description: 'Dry Run Maven release')
  }
  stages {

    stage('Validate') {
      when {
        allOf {
          expression { params.RELEASE }
          not {
            branch 'master'
          }
        }
      }
      steps {
        script {
          error('Releases are only allowed from the master branch.')
        }
      }
    }

    stage('Setup') {
      steps {
        script {
          // Branch-qualified version is workspace-only and is never committed.
          // Example: feature/foo -> 0.0.7-FEATURE-FOO-SNAPSHOT
          if (env.BRANCH_NAME != 'dev' && env.BRANCH_NAME != 'master' && !params.RELEASE) {
            def suffix = env.BRANCH_NAME.replaceAll('[^a-zA-Z0-9.-]', '-').toUpperCase()
            sh """
              BASE_VERSION=\$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout | sed 's/-SNAPSHOT//')
              mvn versions:set \
                -DnewVersion=\${BASE_VERSION}-${suffix}-SNAPSHOT \
                -DgenerateBackupPoms=false \
                -DprocessAllModules=true
            """
          }

          env.VERSION = sh(
            returnStdout: true,
            script: "./build/get-version.sh ${params.RELEASE}"
          ).trim()

          echo "Build version: ${env.VERSION}"
        }
      }
    }

    stage('Maven Spotless') {
      steps {
        withMaven(globalMavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig1387378707709',
                  mavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig1396361652540',
                  traceability: true) {
          sh 'mvn spotless:check'
        }
      }
    }

    stage('Maven build') {
      when {
        not {
          expression { params.RELEASE }
        }
      }
      steps {
        withMaven(globalMavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig1387378707709',
                  mavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig1396361652540',
                  traceability: true) {
          sh 'mvn -B clean package dependency:analyze -Pgbif-dev,secrets-dev -U'
        }
      }
    }

    stage('Maven Snapshot release') {
      when {
        allOf {
          not {
            expression { params.RELEASE }
          }
          branch 'dev'
        }
      }
      steps {
        withMaven(globalMavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig1387378707709',
                  mavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig1396361652540',
                  traceability: true) {
          sh 'mvn -B -DskipTests deploy'
        }
      }
    }

    stage('Maven release') {
      when {
        allOf {
          expression { params.RELEASE }
          branch 'master'
        }
      }
      environment {
        RELEASE_ARGS = utils.createReleaseArgs(params.RELEASE_VERSION, params.DEVELOPMENT_VERSION, params.DRY_RUN_RELEASE)
      }
      steps {
        withMaven(globalMavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.GlobalMavenSettingsConfig1387378707709',
                  mavenSettingsConfig: 'org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig1396361652540',
                  traceability: true) {
          git 'https://github.com/gbif/dwc-dp-analyser-service.git'
          sh 'mvn -B -Dresume=false release:prepare release:perform -Pgbif-dev,secrets-dev -U $RELEASE_ARGS'
        }
      }
    }

    stage('Build and push Docker image') {
      steps {
        sh './build/docker-build.sh ${RELEASE} ${VERSION}'
      }
    }
  }

  post {
    success {
      echo 'Pipeline executed successfully!'
    }
    failure {
      echo 'Pipeline execution failed!'
    }
    cleanup {
      deleteDir()
    }
  }
}
