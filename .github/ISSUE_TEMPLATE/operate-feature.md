---
name: Operate feature
about: This is a template to write a new Operate feature or story related to the Epic
title: ''
labels: ["kind/feature", "component/operate"]
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
<!-- [Optional] <Figma Link> -->

### Acceptance Criteria
<!-- [Mandatory field] The assignee will fill the Acceptance Criteria. -->
- [ ]

### Definition of Ready - Checklist
<!-- the assignee will check the DOR. -->

- [ ] The issue has a meaningful title, description, and testable acceptance criteria
- [ ] If documentation needs to be updated, an issue is created in the [camunda-docs](https://github.com/camunda/camunda-docs) repo, and the issue is added to our Operate project board.
- [ ] If HELM charts need to be updated, an issue is created in the [camunda-platform-heml](https://github.com/camunda/camunda-platform-helm) repo, and the issue is added to our Operate project board.
- [ ] Potential Security risks have been considered and added to the product-hub issue risk assessment
- [ ] Cross-team dependencies have been considered

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
