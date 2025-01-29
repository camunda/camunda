---

name: Test Case
about: Create a test case for the automated C8 Cross-Component E2E test suite based on a user flow.
title: ''
labels: kind/test-case
assignees: ''

---

### Description

<!-- Describe the user flow that this test case is based on, including steps, environment (SaaS or SM), and supported versions. -->

### Preconditions ### 
<!-- Here add the things that must be true before an E2E test is called. like providing specific test data -->

### User Story ### 
<!-- [Mandatory field] -->
```Gherkin
Scenario: As a Console user, I can ....

    Given I click ...
    When  I ....
    Then  I ...


```

### Postconditions ### 
<!-- Add necessary action that when it is true, the E2E test has completed its task, like cleaning the database -->


### Environment

- [ ] SaaS
- [ ] SM

### Supported Versions

- Version X.X
- Version Y.Y

### Priority

- High/Medium/Low

### Additional Information

<!-- Add any additional information relevant to the test case, such as references, dependencies, or related issues. -->

### Definition of Ready - Checklist

<!-- The assignee will check the DOR. -->

- [ ] The test case has a meaningful title, description, and testable acceptance criteria


