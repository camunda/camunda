
import com.atlassian.jira.ComponentManager
import com.atlassian.jira.issue.Issue
import Category
import com.atlassian.jira.issue.link.IssueLink;

log = Category.getInstance("com.onresolve.jira.groovy.SubTasksAssignedToMe")
passesCondition = true

// Add other link types here, however for Blocks, to get a similar meaning,
// you will need to check inward links
linkType = ["subtask"]

linkMgr = ComponentManager.getInstance().getIssueLinkManager()
for (IssueLink link in linkMgr.getOutwardLinks(issue.id)) {
  if (linkType.contains(link.issueLinkType.name)) {
    if (! link.getDestinationObject().resolutionId) {
      passesCondition = false
    }
  }

}

if (issue.issueType.name == 'Feature Request' && passesCondition) {
  def urlBase = 'https://api.trello.com/1/cards'
  def queryParams =
      'name=' + java.net.URLEncoder.encode(issue.summary, "UTF-8") +
      '&desc=' + java.net.URLEncoder.encode(issue.description, "UTF-8") +
      '&idList=5a7adb393d41337d452c77c1&keepFromSource=all&key=af2eff0e801b75b6c70d30f18bc4d800&token=c89d1c922d522e15c178f6322d92c7e54306ec286a467b92835c7e5bf2d5aa38'
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