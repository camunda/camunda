---
name: Test Case
about: Create a test case for the automated Core Application E2E test suite based on a user flow.
title: "E2E - [Module or Area Name] - [Short scenario description]" # e.g., 'E2E - Tasklist - User completes form with dropdown input'
labels:
  - kind/test-case
  - core-application-e2e-test-suite
assignees: ""
---

---

### Description

<!-- Describe the user flow that this test case is based on, including steps, environment (SaaS or SM), and supported versions. -->

### Preconditions

<!-- Things that must be set up before the test starts.
E.g., existing process instance, logged in as specific user, etc. -->

### Test Data

<!-- [Mandatory field] -->
<!-- Specific data used during the test: e.g., forms, diagrams, etc.. -->

### User Story

<!-- [Mandatory field] -->

```Gherkin
Scenario: As a user, I can ...

    Given I ...
    When I ...
    Then I ...
```

### Postconditions

<!-- Add necessary action that when it is true, the E2E test has completed its task, like cleaning the database -->

### Supported Versions

- Version X.X
- Version Y.Y

### Priority

- [ ] High
- [ ] Medium
- [ ] Low

### Additional Information

<!-- Add any additional information relevant to the test case, such as references, dependencies, related issues or screen recording -->

### Definition of Ready - Checklist

<!-- The assignee will check the DOR. -->

- [ ] The test case has a meaningful title, description, and testable acceptance criteria
