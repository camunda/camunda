pipeline {
    agent { label 'ubuntu-large' }
    stages {
        stage('Test') {
            steps {
                sh '/opt/maven/bin/mvn -B clean install'
            }
        }
    }

    post {
        always {
            setGitHubPullRequestStatus
        }
    }
}
