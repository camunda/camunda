import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.label.LabelManager

LabelManager labelManager = ComponentAccessor.getComponent(LabelManager)
def labels = labelManager.getLabels(issue.id).collect{it.getLabel()}
def passesCondition = labels.toSet().contains('needs_testing')

if (passesCondition) {
  def urlBase = 'https://api.trello.com/1/cards'
  def issueLink = 'https://app.camunda.com/jira/browse/' + issue.key
  def description = issue.description == null ? ("\n" + issueLink) : (issue.description + "\n" + issueLink)
  def queryParams =
      'name=' + java.net.URLEncoder.encode(issue.summary, "UTF-8") +
      '&desc=' + java.net.URLEncoder.encode(description, "UTF-8") +
      '&idList=5a7adb393d41337d452c77c1&keepFromSource=all&key=af2eff0e801b75b6c70d30f18bc4d800&token=e8cfbc069d19337974ce9cfb21e6392e9d386b511f1ad7d3f6c7b3a98e43628c'
  def baseUrl = new URL(urlBase + '?' + queryParams)
  def queryString = ''
  def connection = baseUrl.openConnection()

  connection.with {
    doOutput = true
    requestMethod = 'POST'
    outputStream.withWriter { writer ->
      writer << queryString
    }
    println content.text
  }
}