---

name: Operate bug report
about: Report a problem about Operate and help us to fix it.
title: ''
labels: ["kind/bug", "component/operate"]
assignees: ''

---

### Describe the bug

<!-- A clear and concise description of what the problem/bug is about and what is the current behavior. -->

### To Reproduce

Steps to reproduce the behavior:
1. Go to '...'
2. Click on '....'
3. Scroll down to '....'

### Current behavior

<!-- Please attach screenshots, a screen recording, or a file (e.g. the BPMN/DMN/Form file) that has the problem you are describing to help us better debug the respective issue. -->

### Expected behavior

<!-- A clear and concise description of what you expected to happen. -->

### Environment

<!-- Please provide details about the environment you were in when the problem occurred. -->
- OS: [e.g. MacOS]
- Browser: [e.g. chrome, safari]
- Operate Version: [e.g. 8.1]

### Additional context

<!-- Please add any other context about the problem. Here you can also provide us some data that you used while the bug happen like **json** file or specific **BPMN**. -->

---------------------------------------------------------------------------------------------

<!-- As the creator of the issue, you don't have to fill anything below this line, but the assignee will take care of this as part. -->

### Acceptance Criteria

<!-- the assignee will fill the Acceptance Criteria. -->

### Definition of Ready - Checklist

<!-- the assignee will check the DOR. -->
- [ ] The bug has been reproduced by the assignee in the environment compatible with the provided one; otherwise, the issue is closed with a comment
- [ ] The issue has a meaningful title, description, and testable acceptance criteria
- [ ] The issue has been labeled with an appropriate `Bug-area` label
- [ ] Necessary screenshots, screen recordings, or files are attached to the bug report

For UI changes required to solve the bug:

- [ ] Design input has been collected by the assignee

### Implementation

#### :mag: Root Cause Analysis

<!-- Explain the underlying cause for the issue. -->

#### :thought_balloon: Proposed Solution

<!-- Provide a high level overview of the proposed solution. (Technical details will be available in the associated PR) -->

### :point_right: Handover Dev to QA

<!--As a team, we have settled on a checklist to remind the DRI what information to provide to help the QA Engineer perform a frictionless and targeted QA test. The information requested by the checklist can be added before review/moving the ticket to the QA test column as a comment on the ticket.-->
- Changed components:
- Side effects on other components:
- Handy resources:
  BPMN/DMN models, plugins, scripts, REST API endpoints + example payload, etc :
  <!-- Add here -->
- Example projects:

<!-- Add here -->
- Commands/Steps needed to test; Versions to validate:

<!-- Add here -->
- Docker file / HELM chart : in case that it needed to be tested via docker share the version contain the fixed along with version of other services .

<!--elasticsearch: 16.2.2
identitiy:alpha3
zeebe:alpha3
Operate:alpha3
tasklist:alpha3-->
- Release version ( in which version this fixed/feature will be released):

<!-- Add here -->

### :green_book: Link to the test case

<!-- please add test case link for this bug if there is any if not after testing QA will  create a test case for it and add it here. -->
