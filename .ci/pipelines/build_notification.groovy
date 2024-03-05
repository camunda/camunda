void buildNotification(String buildStatus) {
    // build status of null means successful
    buildStatus = buildStatus ?: 'SUCCESS'

    String buildResultUrl = "${env.BUILD_URL}"
    if (env.RUN_DISPLAY_URL) {
        buildResultUrl = "${env.RUN_DISPLAY_URL}"
    }

    def subject = "[${buildStatus}] - ${env.JOB_NAME} - Build #${env.BUILD_NUMBER}"
    def body = "See: ${buildResultUrl}"

    def fallbackRecipients = "svetlana.dorokhova@camunda.com ralf.puchert@camunda.com"

    emailext subject: subject, body: body, to: emailextrecipients([[$class: 'CulpritsRecipientProvider'], [$class: 'RequesterRecipientProvider']]) ?: fallbackRecipients
}

return this