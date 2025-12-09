#!/usr/bin/env bash
set -euo pipefail

# Script to create QA sub-issues for the Audit Log QA Epic
# Based on the breakdown from https://github.com/camunda/camunda/issues/39044
# Target parent epic: https://github.com/camunda/product-hub/issues/3178

ORG_NAME="camunda"
REPO_NAME="product-hub"
PARENT_EPIC="3178"

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}QA Epic Sub-Issues Creation Script${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""
echo "Parent Epic: https://github.com/${ORG_NAME}/${REPO_NAME}/issues/${PARENT_EPIC}"
echo "Target Repository: ${ORG_NAME}/${REPO_NAME}"
echo ""

# Check if gh CLI is available
if ! command -v gh &> /dev/null; then
    echo -e "${YELLOW}Warning: GitHub CLI (gh) is not installed.${NC}"
    echo "This script will output the commands for you to run manually."
    echo ""
fi

# Array to store issue creation commands
declare -a ISSUE_COMMANDS

# M1: Core audit log tracking - QA
ISSUE_COMMANDS+=("gh issue create --repo ${ORG_NAME}/${REPO_NAME} \\
  --title 'QA: Audit Log M1 - Core audit log tracking' \\
  --body '### Description

QA testing for Audit Log M1: Core audit log tracking feature.

This covers end-to-end testing of the core audit log mechanism using Process Instance Modification operation.

### Engineering Epic

https://github.com/camunda/camunda/issues/40519

### Parent QA Epic

https://github.com/camunda/product-hub/issues/${PARENT_EPIC}

### QA Scope

1. Test Process Instance Modification audit log handler
   - Verify audit log entries are created with correct schema
   - Validate data mapping from Zeebe events to audit log entries
   - Test modification-specific data is captured correctly

2. Test REST API search and get endpoints
   - Verify all filters work correctly
   - Test sorting functionality
   - Validate pagination
   - Test authorization with READ_PROCESS_DEFINITION permission

3. Test permissions and authorization
   - Verify atomic permissions work correctly
   - Test composite permissions
   - Validate unauthorized access is blocked

4. Test Java client search command
   - Verify all filters and sorts work correctly
   - Test error handling

5. Test authentication data in Zeebe records
   - Verify authentication data is included in event records
   - Test authentication data in rejection records

6. Test RDBMS exporter handler
   - Verify audit logs are exported correctly
   - Test data integrity

7. Test ILM policies and data cleanup
   - Verify default ILM policies work
   - Test TTL policies for data cleanup

8. Test configurable audit log tracking
   - Verify variable tracking toggle works
   - Test audit log levels configuration

9. Test authorization resource types and permissions
   - Verify dedicated audit log authorization works
   - Test permission checks

10. Performance and Load Testing
    - Execute high-load benchmark tests
    - Identify performance bottlenecks
    - Validate system behavior under stress

### Test Types

- [ ] Unit tests review
- [ ] Integration tests
- [ ] E2E tests
- [ ] Performance tests
- [ ] Security tests
- [ ] Acceptance tests

### Estimate

3-4 weeks for comprehensive QA coverage
' \\
  --label 'qa,audit-log'")

# M2: Track process operations - QA
ISSUE_COMMANDS+=("gh issue create --repo ${ORG_NAME}/${REPO_NAME} \\
  --title 'QA: Audit Log M2 - Track process operations' \\
  --body '### Description

QA testing for Audit Log M2: Track BPMN-related and Decision-related operations.

### Engineering Epic

https://github.com/camunda/camunda/issues/40534

### Parent QA Epic

https://github.com/camunda/product-hub/issues/${PARENT_EPIC}

### QA Scope

1. Test Batch process operations audit logging
   - Cancel process instances in a batch
   - Resolve process incidents in a batch
   - Migrate process instances in a batch
   - Modify process instances in a batch

2. Test Batch management operations audit logging
   - Cancel batch operation
   - Suspend batch operation
   - Resume batch operation

3. Test Migrate process instance operations
   - Verify audit logs for migration operations
   - Test data completeness

4. Test Cancel process instance operations
   - Verify audit logs for cancellation
   - Validate operation details

5. Test Create process instance operations
   - Verify audit logs for instance creation
   - Test all creation methods

6. Test Resolve incident operations
   - Verify audit logs for incident resolution
   - Test resolution details

7. Test Evaluate decision operations
   - Verify audit logs for decision evaluation
   - Test decision-specific data

8. Test Variable update operations
   - Verify audit logs for variable updates
   - Test update tracking
   - Validate old and new values

### Test Types

- [ ] Integration tests
- [ ] E2E tests
- [ ] API tests
- [ ] Acceptance tests

### Estimate

2-3 weeks for comprehensive QA coverage
' \\
  --label 'qa,audit-log'")

# M3: Track Identity operations - QA
ISSUE_COMMANDS+=("gh issue create --repo ${ORG_NAME}/${REPO_NAME} \\
  --title 'QA: Audit Log M3 - Track Identity operations' \\
  --body '### Description

QA testing for Audit Log M3: Track Identity-related operations (ADMIN category).

### Engineering Epic

https://github.com/camunda/camunda/issues/40538

### Parent QA Epic

https://github.com/camunda/product-hub/issues/${PARENT_EPIC}

### QA Scope

1. Test User operations audit logging
   - Create user
   - Update user
   - Delete user

2. Test Tenant operations audit logging
   - Create tenant
   - Update tenant
   - Delete tenant
   - Assign user/client/mapping rule/group/role to tenant
   - Unassign user/client/mapping rule/group/role from tenant

3. Test Role operations audit logging
   - Create role
   - Update role
   - Delete role
   - Assign user/client/mapping rule/group to role
   - Unassign user/client/mapping rule/group from role

4. Test Mapping rule operations audit logging
   - Create mapping rule
   - Update mapping rule
   - Delete mapping rule

5. Test Group operations audit logging
   - Create group
   - Update group
   - Delete group
   - Assign user/client/mapping rule to group
   - Unassign user/client/mapping rule from group

6. Test Authorization operations audit logging
   - Create authorization
   - Update authorization
   - Delete authorization

### Test Types

- [ ] Integration tests
- [ ] E2E tests
- [ ] Identity UI tests
- [ ] API tests
- [ ] Acceptance tests

### Estimate

2-3 weeks for comprehensive QA coverage
' \\
  --label 'qa,audit-log,identity'")

# M4: Track user task operations - QA
ISSUE_COMMANDS+=("gh issue create --repo ${ORG_NAME}/${REPO_NAME} \\
  --title 'QA: Audit Log M4 - Track user task operations' \\
  --body '### Description

QA testing for Audit Log M4: Track user task operations (USER_TASK category).

### Engineering Epic

https://github.com/camunda/camunda/issues/40536

### Parent QA Epic

https://github.com/camunda/product-hub/issues/${PARENT_EPIC}

### QA Scope

1. Test UserTaskRecord changelog
   - Verify old values are tracked correctly
   - Ensure old values are not added for commands
   - Validate old values are included in events

2. Test User task operations audit logging
   - Assign user task
   - Unassign user task
   - Update user task
   - Complete user task

3. Test change tracking
   - Verify all property changes are logged
   - Test changelog format and structure
   - Validate old vs new value tracking

4. Test both Camunda Exporter and RDBMS handlers
   - Verify consistency between exporters
   - Test data integrity

### Test Types

- [ ] Unit tests review
- [ ] Integration tests
- [ ] E2E tests
- [ ] Tasklist UI tests
- [ ] API tests
- [ ] Acceptance tests

### Estimate

1-2 weeks for comprehensive QA coverage
' \\
  --label 'qa,audit-log,tasklist'")

# M5: Track resource operations - QA
ISSUE_COMMANDS+=("gh issue create --repo ${ORG_NAME}/${REPO_NAME} \\
  --title 'QA: Audit Log M5 - Track resource operations' \\
  --body '### Description

QA testing for Audit Log M5: Track Resource-related operations (Operator category).

### Engineering Epic

https://github.com/camunda/camunda/issues/41500

### Parent QA Epic

https://github.com/camunda/product-hub/issues/${PARENT_EPIC}

### QA Scope

1. Test Process CREATED and DELETED operations
   - Verify audit logs for process deployment
   - Test process deletion tracking

2. Test DRG CREATED and DELETED operations
   - Verify audit logs for DRG deployment
   - Test DRG deletion tracking

3. Test Decision CREATED and DELETED operations
   - Verify audit logs for decision deployment
   - Test decision deletion tracking

4. Test Form CREATED and DELETED operations
   - Verify audit logs for form deployment
   - Test form deletion tracking

5. Test Resource CREATED and DELETED operations
   - Verify audit logs for resource deployment
   - Test resource deletion tracking

6. Test visibility in Operate General audit log UI
   - Verify all operations appear in UI
   - Test filtering and search
   - Validate display format

### Test Types

- [ ] Integration tests
- [ ] E2E tests
- [ ] Operate UI tests
- [ ] API tests
- [ ] Acceptance tests

### Estimate

1-2 weeks for comprehensive QA coverage
' \\
  --label 'qa,audit-log,operate'")

# M6: Add operation details - QA (Optional/Should Have)
ISSUE_COMMANDS+=("gh issue create --repo ${ORG_NAME}/${REPO_NAME} \\
  --title 'QA: Audit Log M6 - Add operation details (Should Have)' \\
  --body '### Description

QA testing for Audit Log M6: Additional operation details for audit log entries.

⚠️ This is a SHOULD HAVE epic - only implement if time permits in 8.9 iteration.

### Engineering Epic

https://github.com/camunda/camunda/issues/41196

### Parent QA Epic

https://github.com/camunda/product-hub/issues/${PARENT_EPIC}

### QA Scope

To be defined based on the specific operation details added in the engineering implementation.

### Test Types

- [ ] To be determined based on implementation

### Estimate

1 week for QA coverage (if implemented)
' \\
  --label 'qa,audit-log,should-have'")

# Print all commands
echo -e "${GREEN}Generated issue creation commands:${NC}"
echo ""
echo "=========================================="
echo ""

for i in "${!ISSUE_COMMANDS[@]}"; do
    echo -e "${YELLOW}Issue $((i+1)):${NC}"
    echo "${ISSUE_COMMANDS[$i]}"
    echo ""
    echo "=========================================="
    echo ""
done

# Optionally execute the commands
# Check if running in interactive mode
if [[ -t 0 ]]; then
    echo -e "${BLUE}Do you want to create these issues now? (yes/no)${NC}"
    read -r RESPONSE
else
    # Non-interactive mode - default to no
    RESPONSE="no"
    echo "Running in non-interactive mode. Skipping issue creation."
fi

if [[ "$RESPONSE" == "yes" ]] || [[ "$RESPONSE" == "y" ]]; then
    if ! command -v gh &> /dev/null; then
        echo -e "${YELLOW}Error: GitHub CLI (gh) is required to create issues automatically.${NC}"
        echo "Please install it from: https://cli.github.com/"
        exit 1
    fi
    
    echo ""
    echo -e "${GREEN}Creating issues...${NC}"
    echo ""
    
    for i in "${!ISSUE_COMMANDS[@]}"; do
        echo -e "${BLUE}Creating issue $((i+1)) of ${#ISSUE_COMMANDS[@]}...${NC}"
        # Using eval here is safe because all commands are constructed within this script
        # and do not contain any user input or external data
        # shellcheck disable=SC2086
        eval "${ISSUE_COMMANDS[$i]}"
        echo -e "${GREEN}✓ Issue $((i+1)) created${NC}"
        echo ""
        # Sleep to avoid rate limiting
        sleep 2
    done
    
    echo ""
    echo -e "${GREEN}✅ All issues created successfully!${NC}"
    echo ""
    echo "Remember to link these issues to the parent epic: https://github.com/${ORG_NAME}/${REPO_NAME}/issues/${PARENT_EPIC}"
else
    echo ""
    echo -e "${YELLOW}Issues not created. You can run the commands above manually.${NC}"
    echo ""
    echo "To save these commands to a file, run:"
    echo "  echo \"no\" | $0 > qa-issues-commands.txt 2>&1"
fi
