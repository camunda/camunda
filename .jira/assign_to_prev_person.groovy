import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.label.LabelManager

def user = ComponentAccessor.jiraAuthenticationContext.getLoggedInUser()

LabelManager labelManager = ComponentAccessor.getComponent(LabelManager)
def labels = labelManager.getLabels(issue.id).collect{it.getLabel()}
if (!labels.toSet().contains('current_release')) {
  labels += 'current_release'
  labelManager.setLabels(user,issue.id,labels.toSet(),false,false)
}