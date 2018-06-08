pipeline {
    agent { label 'ubuntu-large' }

    options {
        buildDiscarder(logRotator(numToKeepStr:'10'))
        timestamps()
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {
        stage('Test') {
            steps {
                sh '/opt/maven/bin/mvn -B clean install'
            }
        }
    }

}
