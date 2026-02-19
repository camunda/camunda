import os
import requests
import time
from datetime import datetime, timezone

# CONFIGURATION
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN")
REPO = "camunda/camunda"
RENOVATE_BOT = "renovate[bot]"
DAYS_THRESHOLD = 7
STALE_LABEL = "stale"
DRY_RUN = os.environ.get("DRY_RUN", "true").lower() in ("true", "1", "yes")
REMINDER_DAYS_THRESHOLD = 21

# GitHub Projects v2 Configuration
PROJECT_ID = "PVT_kwDOACVKPs4BKNKD"  # Project number 224 from URL https://github.com/orgs/camunda/projects/224
STATUS_FIELD_ID = "PVTSSF_lADOACVKPs4BKNKDzg6JEH8"  # Status field ID
DELAYED_OPTION_ID = "47fc9ee4"  # "Delayed" option ID

TEAM_SLUGS = {
    "area/backend": "monorepo-backend-engineers",
    "area/frontend": "monorepo-frontend-engineers",
    "area/build": "monorepo-devops-engineers"
}

# Polling parameters
MAX_POLL_SECONDS = 10
POLL_INTERVAL = 3

COMMENT_TEMPLATE_DETECTION_LINE = "assigned as the DRI for this Renovate PR"

COMMENT_TEMPLATE = (
    "_this is an automated message_\n\n"
    "Hi @{assignee},\n\n"
    "You have been " + COMMENT_TEMPLATE_DETECTION_LINE + ". "
    "Please review and process it according to our Renovate PR Handling policy: "
    "https://github.com/camunda/camunda/wiki/Renovate-PR-Handling#dri-responsibilities\n\n"
    "Thank you!"
)

REMINDER_COMMENT_DETECTION_LINE = "reminder about your assigned Renovate PR"

REMINDER_COMMENT_TEMPLATE = (
    "_this is an automated message_\n\n"
    "Hi @{assignee},\n\n"
    "This is a friendly " + REMINDER_COMMENT_DETECTION_LINE + " that has been assigned for at least " + str(REMINDER_DAYS_THRESHOLD) + " days. "
    "Please make sure to work on it according to our Renovate PR Handling policy soon: "
    "https://github.com/camunda/camunda/wiki/Renovate-PR-Handling#dri-responsibilities\n\n"
    "Thank you!"
)

headers = {
    "Authorization": f"token {GITHUB_TOKEN}",
    "Accept": "application/vnd.github+json"
}

# GraphQL headers for GitHub Projects v2 API
graphql_headers = {
    "Authorization": f"bearer {GITHUB_TOKEN}",
    "Accept": "application/vnd.github.v4+json",
    "Content-Type": "application/json"
}

def get_open_prs():
    prs = []
    page = 1
    while True:
        url = f"https://api.github.com/repos/{REPO}/pulls?state=open&per_page=100&page={page}"
        print(f"Retrieving PRs: page {page} ...", end=" ")
        resp = requests.get(url, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        pr_count = len(data)
        print(f"found {pr_count} PR(s).")
        if not data:
            break
        prs.extend(data)
        page += 1
    print(f"Total open PRs retrieved: {len(prs)}\n")
    return prs

def get_labels(pr):
    return [label['name'] for label in pr['labels']]

def request_team_review(pr_number, team_slug):
    if DRY_RUN:
        print(f"[DRY-RUN] Would request review from team '{team_slug}' for PR #{pr_number}")
        return True  # Assume success in dry-run
    url = f"https://api.github.com/repos/{REPO}/pulls/{pr_number}/requested_reviewers"
    payload = {"team_reviewers": [team_slug]}
    resp = requests.post(url, headers=headers, json=payload)
    if resp.status_code == 201:
        print(f"Requested review from team '{team_slug}' for PR #{pr_number}")
        return True
    elif resp.status_code == 422 and "team_reviewers" in resp.text:
        print(f"Team '{team_slug}' cannot be assigned as reviewer for PR #{pr_number}: {resp.text}")
        return False
    else:
        resp.raise_for_status()
        return True

def poll_for_team_reviewer(pr_number, team_slug):
    """Poll PR for assigned reviewers, and return first user who is a member of the team."""
    if DRY_RUN:
        print(f"[DRY-RUN] Would poll for reviewer from team '{team_slug}' for PR #{pr_number}")
        return None  # No reviewer in dry-run

    org = REPO.split('/')[0]
    team_url = f"https://api.github.com/orgs/{org}/teams/{team_slug}/members"
    team_resp = requests.get(team_url, headers=headers)
    team_resp.raise_for_status()
    team_members = set([member['login'] for member in team_resp.json()])

    elapsed = 0
    while elapsed < MAX_POLL_SECONDS:
        url = f"https://api.github.com/repos/{REPO}/pulls/{pr_number}"
        resp = requests.get(url, headers=headers)
        resp.raise_for_status()
        pr_data = resp.json()
        reviewers = pr_data.get('requested_reviewers', [])
        for reviewer in reviewers:
            login = reviewer['login']
            if login in team_members:
                print(f"GitHub picked reviewer {login} from team '{team_slug}' for PR #{pr_number}")
                return login
        time.sleep(POLL_INTERVAL)
        elapsed += POLL_INTERVAL
    print(f"No reviewer assigned from team '{team_slug}' after {MAX_POLL_SECONDS} seconds for PR #{pr_number}")
    return None

def assign_pr(pr_number, username):
    if DRY_RUN:
        print(f"[DRY-RUN] Would assign PR #{pr_number} to {username}")
        return
    url = f"https://api.github.com/repos/{REPO}/issues/{pr_number}/assignees"
    resp = requests.post(url, headers=headers, json={"assignees": [username]})
    resp.raise_for_status()
    print(f"Assigned PR #{pr_number} to {username}")

def post_dri_comment(pr_number, assignee):
    comment = COMMENT_TEMPLATE.format(assignee=assignee)
    if DRY_RUN:
        print(f"[DRY-RUN] Would post comment on PR #{pr_number}:\n{comment}\n")
        return
    url = f"https://api.github.com/repos/{REPO}/issues/{pr_number}/comments"
    resp = requests.post(url, headers=headers, json={"body": comment})
    resp.raise_for_status()
    print(f"Posted DRI comment on PR #{pr_number} mentioning @{assignee}")

def add_label_to_pr(pr_number, label):
    if DRY_RUN:
        print(f"[DRY-RUN] Would add label '{label}' to PR #{pr_number}")
        return
    url = f"https://api.github.com/repos/{REPO}/issues/{pr_number}/labels"
    resp = requests.post(url, headers=headers, json={"labels": [label]})
    resp.raise_for_status()
    print(f"Added label '{label}' to PR #{pr_number}")

def pr_has_reviewers_or_teams_and_assignee(pr_number):
    url = f"https://api.github.com/repos/{REPO}/pulls/{pr_number}"
    resp = requests.get(url, headers=headers)
    resp.raise_for_status()
    pr_data = resp.json()
    user_reviewers = pr_data.get('requested_reviewers', [])
    team_reviewers = pr_data.get('requested_teams', [])
    assignees = pr_data.get('assignees', [])
    has_reviewers = (bool(user_reviewers) or bool(team_reviewers)) and bool(assignees)
    return has_reviewers

def get_pr_comments(pr_number):
    """Get all comments for a PR."""
    comments = []
    page = 1
    while True:
        url = f"https://api.github.com/repos/{REPO}/issues/{pr_number}/comments?per_page=100&page={page}"
        resp = requests.get(url, headers=headers)
        resp.raise_for_status()
        data = resp.json()
        if not data:
            break
        comments.extend(data)
        page += 1
    return comments

def check_dri_assignment_status(pr_number):
    """
    Check if PR has an assignee and if it needs a reminder.
    Returns tuple: (assignee_login, assignment_age_days, has_reminder_comment)
    """
    # Get PR data to check for assignees
    url = f"https://api.github.com/repos/{REPO}/pulls/{pr_number}"
    resp = requests.get(url, headers=headers)
    resp.raise_for_status()
    pr_data = resp.json()

    assignees = pr_data.get('assignees', [])
    if not assignees:
        return None, 0, False

    # Use the first assignee if multiple exist
    assignee = assignees[0]['login']

    # Get comments to find DRI assignment comment and check for reminders
    comments = get_pr_comments(pr_number)
    has_reminder = any(REMINDER_COMMENT_DETECTION_LINE in comment['body'] for comment in comments)

    # Find the DRI assignment comment to get the assignment timestamp
    dri_comment_date = None
    for comment in comments:
        if COMMENT_TEMPLATE_DETECTION_LINE in comment['body']:
            dri_comment_date = datetime.fromisoformat(comment['created_at'].replace("Z", "+00:00"))
            break

    # If no DRI comment found, fall back to PR creation date
    if dri_comment_date is None:
        dri_comment_date = datetime.fromisoformat(pr_data['created_at'].replace("Z", "+00:00"))

    # Calculate age based on DRI assignment comment date
    now = datetime.now(timezone.utc)
    age_days = (now - dri_comment_date).days

    return assignee, age_days, has_reminder

def post_reminder_comment(pr_number, assignee):
    """Post a reminder comment to a DRI about their assigned PR."""
    comment = REMINDER_COMMENT_TEMPLATE.format(assignee=assignee)
    if DRY_RUN:
        print(f"[DRY-RUN] Would post reminder comment on PR #{pr_number}:\n{comment}\n")
        return
    url = f"https://api.github.com/repos/{REPO}/issues/{pr_number}/comments"
    resp = requests.post(url, headers=headers, json={"body": comment})
    resp.raise_for_status()
    print(f"Posted reminder comment on PR #{pr_number} for @{assignee}")

def get_project_item_id(pr_number):
    """Get the project item ID for a PR."""
    # First get the PR node ID
    url = f"https://api.github.com/repos/{REPO}/pulls/{pr_number}"
    resp = requests.get(url, headers=headers)
    resp.raise_for_status()
    pr_data = resp.json()

    # Query to find the project item for this PR
    query = """
    query($projectId: ID!) {
      node(id: $projectId) {
        ... on ProjectV2 {
          items(first: 100) {
            nodes {
              id
              content {
                ... on PullRequest {
                  id
                  number
                }
              }
            }
          }
        }
      }
    }
    """

    variables = {"projectId": PROJECT_ID}

    if DRY_RUN:
        print(f"[DRY-RUN] Would query project item ID for PR #{pr_number}")
        return "dummy_project_item_id"

    resp = requests.post("https://api.github.com/graphql",
                        headers=graphql_headers,
                        json={"query": query, "variables": variables})
    resp.raise_for_status()
    data = resp.json()

    if 'errors' in data:
        raise RuntimeError(f"GraphQL error: {data['errors']}")

    # Find the project item for this PR
    items = data['data']['node']['items']['nodes']
    for item in items:
        content = item.get('content', {})
        if content.get('number') == pr_number:
            return item['id']

    return None  # PR not found in project

def update_project_item_status(pr_number, project_item_id):
    """Update the project item status to 'Delayed'."""

    mutation = """
    mutation($projectId: ID!, $itemId: ID!, $fieldId: ID!, $optionId: String!) {
      updateProjectV2ItemFieldValue(input: {
        projectId: $projectId
        itemId: $itemId
        fieldId: $fieldId
        value: {
          singleSelectOptionId: $optionId
        }
      }) {
        projectV2Item {
          id
        }
      }
    }
    """

    variables = {
        "projectId": PROJECT_ID,
        "itemId": project_item_id,
        "fieldId": STATUS_FIELD_ID,
        "optionId": DELAYED_OPTION_ID
    }

    if DRY_RUN:
        print(f"[DRY-RUN] Would update project status to 'Delayed' for PR #{pr_number} (item ID: {project_item_id})")
        return

    resp = requests.post("https://api.github.com/graphql",
                        headers=graphql_headers,
                        json={"query": mutation, "variables": variables})
    resp.raise_for_status()
    data = resp.json()

    if 'errors' in data:
        raise RuntimeError(f"GraphQL error: {data['errors']}")

    print(f"Updated project status to 'Delayed' for PR #{pr_number}")

def update_project_status_to_delayed(pr_number):
    """Update the GitHub project status to 'Delayed' for the given PR."""
    try:
        project_item_id = get_project_item_id(pr_number)
        if project_item_id:
            update_project_item_status(pr_number, project_item_id)
        else:
            print(f"PR #{pr_number} not found in project {PROJECT_ID} - skipping status update")
    except Exception as e:
        print(f"Error updating project status for PR #{pr_number}: {e}")

def main():
    if not GITHUB_TOKEN:
        raise RuntimeError("GITHUB_TOKEN environment variable is not set.")

    prs = get_open_prs()
    now = datetime.now(timezone.utc)
    for pr in prs:
        pr_user = pr['user']['login']
        if pr_user != RENOVATE_BOT:
            continue

        created_at = datetime.fromisoformat(pr['created_at'].replace("Z","+00:00"))
        age_days = (now - created_at).days
        labels = get_labels(pr)
        pr_number = pr['number']

        # Skip assignment logic if PR is not old enough
        if age_days < DAYS_THRESHOLD:
            continue

        print(f"Processing PR #{pr_number} with URL https://github.com/camunda/camunda/pull/{pr_number}")

        if not STALE_LABEL in labels:
            add_label_to_pr(pr_number, STALE_LABEL)
            labels.append(STALE_LABEL)  # Update local labels list

        assigned = False
        for area_label, team_slug in TEAM_SLUGS.items():
            if area_label in labels:
                if pr_has_reviewers_or_teams_and_assignee(pr_number):
                    print(f"PR #{pr_number} already has reviewers/teams AND assignee picked. Skipping team reviewer assignment.")
                    assigned = True

                elif request_team_review(pr_number, team_slug):
                    reviewer = poll_for_team_reviewer(pr_number, team_slug)
                    if reviewer:
                        assign_pr(pr_number, reviewer)
                        post_dri_comment(pr_number, reviewer)
                        assigned = True
                    else:
                        print(f"Could not assign reviewer for PR #{pr_number}")

                # Stop here since the PR had the correct area label
                break
        if not assigned:
            print(f"PR #{pr['number']} not assigned reviewer (no area label match)")
            continue

        # Check if PR needs a reminder for assigned user
        assignee, assignment_age_days, has_reminder = check_dri_assignment_status(pr_number)
        if assignee and assignment_age_days >= REMINDER_DAYS_THRESHOLD and not has_reminder:
            post_reminder_comment(pr_number, assignee)
            update_project_status_to_delayed(pr_number)

if __name__ == "__main__":
    main()
