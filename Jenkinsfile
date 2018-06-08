pipeline {
    agent none
    stages {
        stage('Test') {
            agent {
                docker { image 'maven:3.5.3-jdk-8-alpine' }
            }
            steps {
                sh 'mvn clean install'
            }
        }
    }
}
