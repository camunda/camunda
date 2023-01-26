---
name: Feature
about: This is a template to write a new feature or story related to the Epic
title: ''
labels: kind/feature
assignees: ''

---
<!-- Remember to link this feature issue in the parent Epic -->

### Description
<!-- [Optional] A clear description of the story -->

### User Story
<!-- [Mandatory field] -->
```Gherkin
Scenario: As an Operate user
    Given I am logged in
    When I select one item/tab in the toolbar
    Then this item should be shown in highlighted (in bold)
```
### Designs
<!-- [Optional] <Zeplin Link> -->

### Acceptance Criteria
<!-- [Mandatory field] The assignee will fill the Acceptance Criteria. -->
- [ ]

### Definition of Ready - Checklist
<!-- the assignee will check the DOR. -->

- [ ] The issue has a meaningful title, description, and testable acceptance criteria

Optional:
- [ ] Design input has been collected by the assignee
### :point_right: Handover Dev to QA 
<!--As a team, we have settle in a checklist to remind the DRI what information to provide to help the QA Engineer perform a friction less and targeted QA test. The information requested by the checklist can be added before review/move the ticket to the QA test column as a comment on the ticket.-->

- Handy resources: 
     BPMN/DMN models, plugins, scripts, REST API endpoints + example payload, etc : 
     <!-- Add here -->
- Example projects:
<!-- Add here -->

- Versions to validate:
> Docker file : in case that it needed to be tested via docker share the version contain the fixed along with version of other services . 
<!--elasticsearch: 16.2.2
identitiy:alpha3
zeebe:alpha3
Operate:alpha3
tasklist:alpha3-->
- Release version ( in which version this fixed/feature will be released)
<!-- Add here -->


### :green_book: Link to the test case 
<!-- please add test case link for this bug if there is any if not after testing QA will  create a test case for it and add it here. -->
