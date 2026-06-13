# Answer: Can we move/host this agent in team-qa-engineering?

## Short Answer

**YES!** The Test Case Generator Agent can and should be moved to `camunda/team-qa-engineering`.

## Why This Makes Sense

1. ✅ **Clear Ownership**: QA team owns test case generation
2. ✅ **No Camunda Dependencies**: Agent uses only standard libraries
3. ✅ **Faster Iteration**: QA team can iterate independently
4. ✅ **Better Fit**: QA tooling belongs in QA repository
5. ✅ **Simple Extraction**: Straightforward to extract and deploy

## How to Move It

### Quick Migration (Automated)

```bash
# Run the extraction script
./scripts/extract-testgen-agent.sh

# Copy to team-qa-engineering repo
cd /path/to/team-qa-engineering
tar -xzf /tmp/test-case-generator-agent.tar.gz

# Build and run
./mvnw clean package
java -jar target/test-case-generator-agent-*.jar
```

The extraction script automatically:
- Copies all source files
- Updates package names
- Creates Spring Boot project structure
- Generates Dockerfile and docker-compose
- Creates CI/CD workflow
- Updates scripts for new endpoint

### What Gets Moved

**Source Code:**
- TestCaseGeneratorTools.java (MCP tool)
- GitHubProductHubService.java (GitHub integration)
- TestRailIntegrationService.java (TestRail integration)
- TestCaseTransformer.java (transformation logic)

**Tests:**
- TestCaseGeneratorToolsTest.java
- TestCaseTransformerTest.java

**Documentation:**
- README.md
- USAGE_GUIDE.md
- IMPLEMENTATION_SUMMARY.md
- MIGRATION_GUIDE.md
- Postman collection

**Configuration & Scripts:**
- application.yaml
- setup-testgen-agent.sh
- test-testgen-agent.sh

## Architecture After Migration

### Before (Current):
```
camunda/camunda
└── gateways/gateway-mcp/
    └── tool/testgen/
        └── [All agent code]
```

### After (Recommended):
```
camunda/team-qa-engineering
└── test-case-generator-agent/     ← Standalone MCP server
    ├── src/main/java/
    ├── src/test/java/
    ├── docs/
    ├── scripts/
    └── Dockerfile
```

## Deployment Options

### 1. Standalone Service (Recommended)
```bash
# Docker
docker run -p 8081:8081 \
  -e GITHUB_TOKEN=$GITHUB_TOKEN \
  -e TESTRAIL_URL=$TESTRAIL_URL \
  -e TESTRAIL_USERNAME=$TESTRAIL_USERNAME \
  -e TESTRAIL_API_KEY=$TESTRAIL_API_KEY \
  camunda/test-case-generator-agent

# Or docker-compose
docker-compose up
```

### 2. Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: test-case-generator
spec:
  replicas: 2
  template:
    spec:
      containers:
      - name: agent
        image: camunda/test-case-generator-agent:latest
        ports:
        - containerPort: 8081
```

### 3. JAR File
```bash
java -jar test-case-generator-agent.jar \
  --server.port=8081
```

## Benefits of Moving

### For QA Team
- ✅ Full control and ownership
- ✅ Fast iteration and deployment
- ✅ Independent versioning
- ✅ Can customize for QA needs
- ✅ Easier to maintain

### For Product Team
- ✅ Cleaner separation of concerns
- ✅ Less complexity in main repo
- ✅ Smaller build/test surface
- ✅ No product code mixed with QA tooling

### For Everyone
- ✅ Clear boundaries
- ✅ Independent deployment
- ✅ Better reusability
- ✅ Simpler CI/CD

## Technical Details

### Dependencies (All Standard)
- Spring Boot - web framework
- Spring AI MCP Server - MCP protocol
- Jackson - JSON processing
- Java HTTP Client - API calls
- Bean Validation - parameter validation

**No Camunda-specific dependencies!** This makes extraction very clean.

### Port Configuration
- **Current** (in camunda/camunda): `http://localhost:8080/mcp`
- **After migration**: `http://localhost:8081/mcp`

Scripts automatically updated to use new port.

### API Compatibility
The MCP tool interface remains identical. AI assistants just need to update the endpoint URL.

## Migration Timeline

### Immediate (Week 1)
1. Run extraction script
2. Create repo in team-qa-engineering
3. Commit extracted code
4. Set up CI/CD

### Short-term (Week 2-3)
1. Deploy to QA infrastructure
2. Test with existing workflows
3. Update documentation
4. Train team members

### Long-term (Month 2+)
1. Optionally remove from camunda/camunda
2. Add QA-specific enhancements
3. Integrate into QA pipelines

## Risks and Mitigations

### Risk: Breaking existing users
**Mitigation**: Keep both versions during transition period

### Risk: Deployment complexity
**Mitigation**: Dockerfile and docker-compose provided

### Risk: Configuration management
**Mitigation**: Same environment variables, documented

### Risk: CI/CD setup
**Mitigation**: GitHub Actions workflow included

## Decision Matrix

| Factor | Keep in camunda/camunda | Move to team-qa-engineering |
|--------|------------------------|------------------------------|
| Ownership | Product team | ✅ QA team |
| Iteration Speed | Slow | ✅ Fast |
| Deployment | Bundled | ✅ Independent |
| Maintenance | Product priorities | ✅ QA priorities |
| Complexity | Higher | ✅ Lower |
| Dependencies | Many | ✅ Minimal |
| CI/CD Control | Shared | ✅ Dedicated |
| **Overall** | ❌ Not ideal | ✅ **Recommended** |

## Recommendation

**Move the Test Case Generator Agent to team-qa-engineering as a standalone MCP server.**

### Execution Plan

1. **Today**: Run `./scripts/extract-testgen-agent.sh`
2. **This Week**: Create repo structure in team-qa-engineering
3. **Next Week**: Deploy and test
4. **Following Week**: Announce migration to users

### Support During Migration

The following documentation is included:
- Complete migration guide (MIGRATION_GUIDE.md)
- Updated usage guide
- Deployment examples
- Troubleshooting tips

## Getting Started with Migration

```bash
# 1. Extract from camunda/camunda
cd /home/runner/work/camunda/camunda
./scripts/extract-testgen-agent.sh

# 2. Move to team-qa-engineering
cd /path/to/team-qa-engineering
mkdir test-case-generator-agent
cd test-case-generator-agent
tar -xzf /tmp/test-case-generator-agent.tar.gz

# 3. Build and test
./mvnw clean package
./mvnw test

# 4. Run locally
java -jar target/test-case-generator-agent-*.jar

# 5. Or with Docker
docker-compose up

# 6. Test endpoint
curl http://localhost:8081/mcp
./scripts/test-testgen-agent.sh all
```

## Questions?

See the comprehensive [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md) for:
- Detailed migration steps
- Alternative approaches
- Deployment options
- CI/CD setup
- Troubleshooting

## Conclusion

Moving the Test Case Generator Agent to `camunda/team-qa-engineering` is:
- ✅ **Technically feasible** (no Camunda dependencies)
- ✅ **Logically correct** (QA tooling in QA repo)
- ✅ **Operationally beneficial** (independent deployment)
- ✅ **Ready to execute** (extraction script provided)

**Recommendation: Proceed with migration.**
