import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.changehistory.ChangeHistoryManager
import com.atlassian.jira.issue.history.ChangeItemBean
import com.atlassian.jira.user.util.UserManager

ChangeHistoryManager changeHistoryManager = ComponentAccessor.getComponent(ChangeHistoryManager);

def assigneeChHistory = changeHistoryManager.getChangeItemsForField(issue, "assignee")

if (!assigneeChHistory.isEmpty()) {
  ChangeItemBean assigneeChItemBean = assigneeChHistory.get(assigneeChHistory.size() - 1)
  previousAssignee = assigneeChItemBean.getFrom()
  userManager = (UserManager)  ComponentAccessor.getUserManager()
  usera = userManager.getUser(previousAssignee);
  issue.setAssignee(usera);
  issue.store();
}


