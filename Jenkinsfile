pipeline {
    agent { label 'ubuntu-large' }
    stages {
        stage('Test') {
            steps {
                sh 'mvn clean install'
            }
        }
    }
}
