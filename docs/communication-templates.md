# Release Communication Templates

This directory contains standardized templates for all release-related communications. These templates ensure consistent messaging and help maintain clear communication during release cycles.

## Template Categories

### 1. Freeze Announcements
- [Feature Freeze Announcement](#feature-freeze-announcement)
- [Code Freeze Announcement](#code-freeze-announcement)
- [Freeze Extension Notice](#freeze-extension-notice)
- [Freeze End Announcement](#freeze-end-announcement)

### 2. Calendar Invites
- [Release Planning Meeting](#release-planning-meeting)
- [Feature Freeze Reminder](#feature-freeze-reminder)
- [Code Freeze Reminder](#code-freeze-reminder)
- [Release Day](#release-day)
- [Post-Release Retrospective](#post-release-retrospective)

### 3. Emergency Communications
- [Emergency Change Request](#emergency-change-request)
- [Critical Issue During Freeze](#critical-issue-during-freeze)
- [Release Delay Notice](#release-delay-notice)

### 4. Stakeholder Updates
- [Executive Summary](#executive-summary)
- [External Customer Communication](#external-customer-communication)
- [Partner/Vendor Notification](#partnervendor-notification)

---

## Freeze Announcements

### Feature Freeze Announcement

**Channel:** Slack `#engineering-releases`, Email to engineering-all@camunda.com
**Subject:** 🚨 Feature Freeze - Release {VERSION} Starting {DATE}

```
🚨 **FEATURE FREEZE ANNOUNCEMENT** 🚨

The feature freeze for **Release {VERSION}** begins on **{DATE}** at **{TIME} UTC**.

## What This Means
- ✅ Complete in-progress features
- ✅ Focus on bug fixes and stabilization  
- ✅ Begin comprehensive testing
- ❌ No new features for this release

## Important Dates
📅 **Feature Freeze:** {FEATURE_FREEZE_DATE}
🔒 **Code Freeze:** {CODE_FREEZE_DATE}  
🚀 **Release Date:** {RELEASE_DATE}
📋 **Retrospective:** {RETRO_DATE}

## Next Steps for Teams
1. **Product Teams:** Finalize feature documentation
2. **QA Teams:** Begin end-to-end testing
3. **Engineering:** Focus on completing open features
4. **DevOps:** Prepare release infrastructure

## Emergency Procedures
For critical changes during the freeze:
1. Contact release manager: @{RELEASE_MANAGER}
2. Provide impact assessment and rollback plan
3. Get approval before implementing changes

## Resources
📖 [Release Communication Guide](https://github.com/camunda/camunda/blob/main/docs/release-communication-guide.md)
📅 [Release Calendar](link-to-calendar)
💬 Questions? Ask in #engineering-releases

---
Release Manager: @{RELEASE_MANAGER}
DevOps Team: @camunda/monorepo-devops-team
```

### Code Freeze Announcement

**Channel:** Slack `#engineering-releases`, Email to engineering-all@camunda.com
**Subject:** 🔒 Code Freeze - Release {VERSION} in Effect

```
🔒 **CODE FREEZE IN EFFECT** 🔒

The code freeze for **Release {VERSION}** is now active until **{END_DATE}**.

## Restrictions in Effect
- ❌ No code changes without approval
- ❌ No new features or enhancements
- ❌ No refactoring or cleanup
- ✅ Critical bug fixes only (with approval)

## Emergency Change Process
**BEFORE making ANY changes:**
1. 🚨 Contact release manager: @{RELEASE_MANAGER}
2. 📝 Provide detailed impact assessment
3. 🔄 Include rollback plan
4. ✅ Wait for explicit approval
5. 📢 Notify team after implementation

## Release Timeline
🔒 **Code Freeze Active:** {CODE_FREEZE_DATE} - {END_DATE}
🚀 **Release Deployment:** {RELEASE_DATE}
📊 **Go/No-Go Decision:** {GO_NOGO_DATE}

## Monitoring & Support
- **Release Dashboard:** {DASHBOARD_URL}
- **Critical Issues:** Create incident in #engineering-releases
- **Release Manager:** @{RELEASE_MANAGER}
- **On-Call DevOps:** @camunda/monorepo-devops-team

## What Happens Next
- Daily freeze status updates in #engineering-releases
- Release readiness assessment 24h before deployment
- Go/No-Go decision meeting on {GO_NOGO_DATE}

⚠️ **Remember:** All changes during freeze must be approved. When in doubt, ask first!

📖 [Release Communication Guide](https://github.com/camunda/camunda/blob/main/docs/release-communication-guide.md)
```

### Freeze Extension Notice

**Channel:** Slack `#engineering-releases`, Email to engineering-all@camunda.com
**Subject:** ⚠️ Freeze Period Extended - Release {VERSION}

```
⚠️ **FREEZE PERIOD EXTENSION** ⚠️

The {FREEZE_TYPE} for **Release {VERSION}** has been extended until **{NEW_END_DATE}**.

## Reason for Extension
{EXTENSION_REASON}

## Updated Timeline
📅 **Original End Date:** {ORIGINAL_END_DATE}
📅 **New End Date:** {NEW_END_DATE}
🚀 **Updated Release Date:** {NEW_RELEASE_DATE}

## Impact Assessment
- **Affected Teams:** {AFFECTED_TEAMS}
- **Downstream Dependencies:** {DEPENDENCIES}
- **Customer Impact:** {CUSTOMER_IMPACT}

## Actions Required
- [ ] Update project timelines
- [ ] Notify stakeholders
- [ ] Adjust testing schedules
- [ ] Review resource allocation

## Communication Plan
- Stakeholder notification: {STAKEHOLDER_NOTIFY_DATE}
- Customer communication: {CUSTOMER_NOTIFY_DATE}
- Partner updates: {PARTNER_NOTIFY_DATE}

Questions? Contact @{RELEASE_MANAGER} or ask in #engineering-releases.
```

### Freeze End Announcement

**Channel:** Slack `#engineering-releases`, Email to engineering-all@camunda.com
**Subject:** ✅ Freeze Period Ended - Release {VERSION} Complete

```
✅ **FREEZE PERIOD ENDED** ✅

The {FREEZE_TYPE} for **Release {VERSION}** has officially ended.

## Release Summary
🚀 **Released:** {RELEASE_DATE}
📊 **Status:** {RELEASE_STATUS}
⏱️ **Duration:** {FREEZE_DURATION}

## Key Metrics
- **Changes During Freeze:** {CHANGE_COUNT}
- **Emergency Approvals:** {EMERGENCY_COUNT}
- **Issues Resolved:** {ISSUE_COUNT}
- **Rollbacks:** {ROLLBACK_COUNT}

## What's Next
🔄 **Normal Development:** Resumes immediately
📋 **Retrospective:** {RETRO_DATE} at {RETRO_TIME}
🎯 **Next Release Planning:** {NEXT_PLANNING_DATE}

## Lessons Learned
{LESSONS_LEARNED}

## Thank You
Thanks to all teams for following freeze protocols and ensuring a successful release!

Next release planning begins {NEXT_PLANNING_DATE}. Check the release calendar for upcoming dates.

📅 [Release Calendar](link-to-calendar)
📋 [Retrospective Notes](link-to-retro)
```

---

## Calendar Invites

### Release Planning Meeting

**Subject:** Release {VERSION} Planning Session
**Duration:** 2 hours
**Attendees:** Engineering leads, Product managers, QA leads, DevOps team

```
📋 Release {VERSION} Planning Session

## Agenda
1. Release scope and timeline review (30 min)
2. Feature prioritization and dependencies (45 min)
3. Resource allocation and capacity planning (30 min)
4. Risk assessment and mitigation (15 min)
5. Communication plan review (10 min)
6. Next steps and action items (10 min)

## Pre-Meeting Preparation
- [ ] Review proposed features list
- [ ] Estimate development effort
- [ ] Identify resource constraints
- [ ] Prepare risk assessments

## Important Dates
📅 Feature Freeze: {FEATURE_FREEZE_DATE}
🔒 Code Freeze: {CODE_FREEZE_DATE}
🚀 Release Date: {RELEASE_DATE}

## Resources
📖 [Release Communication Guide](https://github.com/camunda/camunda/blob/main/docs/release-communication-guide.md)
📊 [Release Planning Template](link-to-template)

Meeting Notes: [Link to shared document]
```

### Feature Freeze Reminder

**Subject:** ⚠️ Feature Freeze - Release {VERSION} - Tomorrow!
**Duration:** 30 minutes
**Attendees:** All engineering teams

```
⚠️ Feature Freeze Reminder - Release {VERSION}

## Tomorrow: Feature Freeze Begins
🕐 **Time:** {TIME} UTC
📅 **Date:** {DATE}

## Final Checklist
- [ ] Complete current feature development
- [ ] Update feature documentation
- [ ] Create feature toggle plans if needed
- [ ] Notify team of completion status
- [ ] Prepare for testing phase

## After Freeze Begins
✅ Bug fixes and stabilization
✅ Testing and validation
✅ Documentation updates
❌ New feature development

## Support
- Questions: #engineering-releases
- Release Manager: @{RELEASE_MANAGER}
- Emergency contact: {EMERGENCY_CONTACT}

This is an automated reminder. No action needed unless you have questions.
```

### Code Freeze Reminder

**Subject:** 🔒 Code Freeze - Release {VERSION} - Starting in 24 Hours
**Duration:** 15 minutes
**Attendees:** All engineering teams, DevOps

```
🔒 Code Freeze Reminder - Release {VERSION}

## Code Freeze Begins in 24 Hours
🕐 **Time:** {TIME} UTC
📅 **Date:** {DATE}

## Final Actions Required
- [ ] Merge all approved changes
- [ ] Complete testing of pending features
- [ ] Update release notes
- [ ] Prepare rollback procedures

## During Code Freeze
✅ Critical bug fixes (with approval)
✅ Documentation updates
✅ Testing and validation
❌ New features or enhancements
❌ Refactoring or cleanup

## Emergency Process
1. Contact @{RELEASE_MANAGER}
2. Provide impact assessment
3. Get explicit approval
4. Document all changes

Last chance to merge non-critical changes!
```

### Release Day

**Subject:** 🚀 Release Day - {VERSION} Deployment
**Duration:** 4 hours
**Attendees:** DevOps team, Release manager, Engineering leads

```
🚀 Release {VERSION} Deployment

## Deployment Schedule
🕐 **Start Time:** {START_TIME} UTC
🕐 **Expected Completion:** {END_TIME} UTC
📍 **War Room:** {LOCATION}

## Deployment Steps
1. **Pre-deployment checks** ({PRE_TIME})
   - [ ] Verify all systems healthy
   - [ ] Confirm rollback procedures
   - [ ] Check monitoring systems

2. **Deployment execution** ({DEPLOY_TIME})
   - [ ] Deploy to staging
   - [ ] Run smoke tests
   - [ ] Deploy to production
   - [ ] Verify deployment

3. **Post-deployment validation** ({POST_TIME})
   - [ ] Run full test suite
   - [ ] Monitor error rates
   - [ ] Verify feature functionality
   - [ ] Confirm performance metrics

## Contacts
📞 **Release Manager:** {RELEASE_MANAGER_PHONE}
📞 **DevOps Lead:** {DEVOPS_LEAD_PHONE}
📞 **On-Call Engineer:** {ONCALL_PHONE}

## Communication
- Live updates: #engineering-releases
- Dashboard: {RELEASE_DASHBOARD}
- Status page: {STATUS_PAGE}

## Rollback Criteria
- Error rate > {ERROR_THRESHOLD}%
- Performance degradation > {PERF_THRESHOLD}%
- Critical functionality failure
```

### Post-Release Retrospective

**Subject:** 📋 Post-Release Retrospective - {VERSION}
**Duration:** 1 hour
**Attendees:** Engineering leads, Release manager, QA leads, Product managers

```
📋 Post-Release Retrospective - Release {VERSION}

## Meeting Purpose
Review the release process, identify improvements, and plan for future releases.

## Agenda
1. **Release overview** (10 min)
   - Timeline and metrics
   - Issues encountered
   - Success metrics

2. **What went well** (15 min)
   - Process improvements
   - Team collaboration
   - Technical achievements

3. **What could be improved** (20 min)
   - Process bottlenecks
   - Communication gaps
   - Technical challenges

4. **Action items for next release** (15 min)
   - Process changes
   - Tool improvements
   - Communication enhancements

## Pre-Meeting Preparation
Please review:
- [ ] Release timeline and metrics
- [ ] Issues that occurred during release
- [ ] Feedback from your team
- [ ] Previous retrospective action items

## Metrics to Review
- Feature freeze violations: {FEATURE_VIOLATIONS}
- Code freeze violations: {CODE_VIOLATIONS}
- Emergency changes: {EMERGENCY_CHANGES}
- Release delay: {DELAY_HOURS} hours
- Rollback events: {ROLLBACKS}

## Previous Action Items
{PREVIOUS_ACTION_ITEMS}

Meeting Notes: [Link to collaborative document]
```

---

## Emergency Communications

### Emergency Change Request

**Channel:** Slack `#engineering-releases`
**Subject:** 🚨 Emergency Change Request - Release {VERSION}

```
🚨 **EMERGENCY CHANGE REQUEST** 🚨

**Requestor:** @{REQUESTOR}
**Release:** {VERSION}
**Priority:** {PRIORITY}
**Estimated Impact:** {IMPACT_LEVEL}

## Issue Description
{DETAILED_DESCRIPTION}

## Proposed Solution
{SOLUTION_DESCRIPTION}

## Impact Assessment
**Customer Impact:** {CUSTOMER_IMPACT}
**System Impact:** {SYSTEM_IMPACT}
**Risk Level:** {RISK_LEVEL}
**Affected Components:** {COMPONENTS}

## Rollback Plan
{ROLLBACK_PROCEDURES}

## Testing Plan
- [ ] Unit tests: {UNIT_TEST_STATUS}
- [ ] Integration tests: {INTEGRATION_TEST_STATUS}
- [ ] Manual testing: {MANUAL_TEST_STATUS}
- [ ] Performance testing: {PERF_TEST_STATUS}

## Timeline
**Proposed Implementation:** {IMPLEMENTATION_TIME}
**Testing Window:** {TESTING_WINDOW}
**Go-Live:** {GOLIVE_TIME}

## Approval Needed From
- [ ] Release Manager: @{RELEASE_MANAGER}
- [ ] Technical Lead: @{TECH_LEAD}
- [ ] Product Manager: @{PRODUCT_MANAGER}

@{RELEASE_MANAGER} - Please review and approve/reject ASAP
```

### Critical Issue During Freeze

**Channel:** Slack `#engineering-releases`
**Subject:** 🔥 Critical Issue Detected - Release {VERSION}

```
🔥 **CRITICAL ISSUE DETECTED** 🔥

**Severity:** {SEVERITY}
**Status:** {STATUS}
**Discovery Time:** {DISCOVERY_TIME}
**Reporter:** @{REPORTER}

## Issue Summary
{ISSUE_SUMMARY}

## Impact Assessment
**Customer Facing:** {CUSTOMER_FACING}
**Business Impact:** {BUSINESS_IMPACT}
**Technical Impact:** {TECHNICAL_IMPACT}
**Affected Users:** {AFFECTED_USERS}

## Current Actions
- [ ] Issue investigation in progress
- [ ] Incident response team assembled
- [ ] Customer communication prepared
- [ ] Fix development started

## Response Team
**Incident Commander:** @{INCIDENT_COMMANDER}
**Technical Lead:** @{TECH_LEAD}
**Communication Lead:** @{COMM_LEAD}
**DevOps Support:** @{DEVOPS_SUPPORT}

## Options Being Considered
1. **Hot Fix:** {HOTFIX_DESCRIPTION} (ETA: {HOTFIX_ETA})
2. **Rollback:** {ROLLBACK_DESCRIPTION} (ETA: {ROLLBACK_ETA})
3. **Workaround:** {WORKAROUND_DESCRIPTION} (ETA: {WORKAROUND_ETA})

## Next Update
Next update in {UPDATE_INTERVAL} minutes or when status changes.

**War Room:** {WAR_ROOM_LOCATION}
**Bridge Line:** {BRIDGE_NUMBER}
```

### Release Delay Notice

**Channel:** Slack `#engineering-releases`, Email to stakeholders
**Subject:** ⚠️ Release {VERSION} Delayed - New Date {NEW_DATE}

```
⚠️ **RELEASE DELAY NOTIFICATION** ⚠️

Release {VERSION} has been delayed from {ORIGINAL_DATE} to {NEW_DATE}.

## Reason for Delay
{DELAY_REASON}

## New Timeline
📅 **Original Release Date:** {ORIGINAL_DATE}
📅 **New Release Date:** {NEW_DATE}
📅 **Delay Duration:** {DELAY_DURATION}

## Impact Analysis
**Customer Impact:** {CUSTOMER_IMPACT}
**Internal Impact:** {INTERNAL_IMPACT}
**Dependencies Affected:** {DEPENDENCIES}

## Updated Schedule
🔒 **Extended Code Freeze:** Until {NEW_FREEZE_END}
🧪 **Additional Testing:** {TESTING_PERIOD}
🚀 **New Deployment Window:** {NEW_DEPLOY_WINDOW}

## Communication Plan
- [ ] Customer notification: {CUSTOMER_NOTIFY_TIME}
- [ ] Partner updates: {PARTNER_NOTIFY_TIME}
- [ ] Stakeholder briefing: {STAKEHOLDER_BRIEF_TIME}
- [ ] Public announcement: {PUBLIC_ANNOUNCE_TIME}

## Actions Taken
{ACTIONS_TAKEN}

## Mitigation Measures
{MITIGATION_MEASURES}

## Contact Information
**Release Manager:** @{RELEASE_MANAGER}
**Stakeholder Questions:** @{STAKEHOLDER_CONTACT}
**Technical Questions:** @{TECH_CONTACT}

We apologize for any inconvenience and are working to minimize the impact of this delay.
```

---

## Stakeholder Updates

### Executive Summary

**Audience:** C-level executives, VPs
**Channel:** Email
**Subject:** Release {VERSION} Executive Summary

```
Release {VERSION} Executive Summary

## Executive Overview
Release {VERSION} {STATUS} on {DATE}, delivering {KEY_FEATURES} to {CUSTOMER_COUNT} customers.

## Key Metrics
📊 **Business Impact**
- Customer satisfaction: {CSAT_SCORE}/10
- Feature adoption: {ADOPTION_RATE}%
- Performance improvement: {PERF_IMPROVEMENT}%
- Cost reduction: {COST_SAVINGS}

📈 **Technical Metrics**  
- Deployment success rate: {DEPLOY_SUCCESS}%
- Release cycle time: {CYCLE_TIME} days
- Bug escape rate: {BUG_ESCAPE}%
- Security vulnerabilities: {SECURITY_ISSUES}

## Strategic Achievements
✅ {ACHIEVEMENT_1}
✅ {ACHIEVEMENT_2}  
✅ {ACHIEVEMENT_3}

## Challenges & Resolutions
🔴 **Challenge:** {CHALLENGE_1}
✅ **Resolution:** {RESOLUTION_1}

## Next Release Preview
📅 **Release {NEXT_VERSION}:** {NEXT_DATE}
🎯 **Key Focus:** {NEXT_FOCUS}
💼 **Business Value:** {NEXT_VALUE}

## ROI Analysis
**Investment:** {INVESTMENT_AMOUNT}
**Return:** {RETURN_AMOUNT}
**Payback Period:** {PAYBACK_PERIOD}

Contact: {RELEASE_MANAGER_NAME}, Release Manager
```

### External Customer Communication

**Audience:** External customers
**Channel:** Customer portal, Email newsletter
**Subject:** Camunda {VERSION} Now Available - Enhanced Performance & New Features

```
🚀 Camunda {VERSION} Now Available

Dear Camunda Community,

We're excited to announce the release of Camunda {VERSION}, featuring significant performance improvements and powerful new capabilities.

## What's New
✨ **{FEATURE_1}:** {FEATURE_1_DESCRIPTION}
⚡ **{FEATURE_2}:** {FEATURE_2_DESCRIPTION}  
🔧 **{FEATURE_3}:** {FEATURE_3_DESCRIPTION}

## Performance Improvements
- {PERFORMANCE_1}: {IMPROVEMENT_1}% faster
- {PERFORMANCE_2}: {IMPROVEMENT_2}% reduction in resource usage
- {PERFORMANCE_3}: {IMPROVEMENT_3}% improved reliability

## Upgrade Information
📋 **Upgrade Guide:** {UPGRADE_GUIDE_URL}
⏱️ **Estimated Downtime:** {DOWNTIME_ESTIMATE}
🆘 **Support:** {SUPPORT_CONTACT}

## Breaking Changes
⚠️ Please review the breaking changes documentation: {BREAKING_CHANGES_URL}

## Migration Assistance
Our team is ready to help with your upgrade:
- Migration consultation: {CONSULTATION_LINK}
- Technical support: {SUPPORT_LINK}
- Community forum: {FORUM_LINK}

## Documentation
📚 **Release Notes:** {RELEASE_NOTES_URL}
📖 **Documentation:** {DOCS_URL}
🎥 **Video Overview:** {VIDEO_URL}

Thank you for being part of the Camunda community!

Best regards,
The Camunda Team

**Need Help?**
Support: {SUPPORT_EMAIL}
Community: {COMMUNITY_URL}
```

### Partner/Vendor Notification

**Audience:** Integration partners, vendors
**Channel:** Partner portal, Email
**Subject:** Partner Update: Camunda {VERSION} Integration Requirements

```
Partner Update: Camunda {VERSION} Release

Dear Camunda Partner,

Camunda {VERSION} is now available with updates that may affect your integration.

## Integration Impact Assessment
🔍 **API Changes:** {API_CHANGES}
🔄 **Protocol Updates:** {PROTOCOL_UPDATES}
📦 **Dependency Changes:** {DEPENDENCY_CHANGES}

## Action Required
Priority: {PRIORITY_LEVEL}
Deadline: {ACTION_DEADLINE}

### For System Integrators
- [ ] Review API changes: {API_DOCS_URL}
- [ ] Test integration compatibility  
- [ ] Update client libraries
- [ ] Validate authentication flows

### For Technology Partners
- [ ] Update connector compatibility
- [ ] Test plugin functionality
- [ ] Verify certification requirements
- [ ] Update marketplace listings

## Technical Resources
📋 **Integration Guide:** {INTEGRATION_GUIDE_URL}
🔧 **API Documentation:** {API_DOCS_URL}
🧪 **Testing Tools:** {TESTING_TOOLS_URL}
📞 **Technical Support:** {TECH_SUPPORT_CONTACT}

## Timeline
📅 **Partner Testing Period:** {TESTING_PERIOD}
📅 **Customer Migration Window:** {MIGRATION_WINDOW}
📅 **Legacy Support End:** {LEGACY_END_DATE}

## Certification Program
If your integration requires re-certification:
- Application deadline: {CERT_DEADLINE}
- Testing period: {CERT_TESTING}
- Certification review: {CERT_REVIEW}

## Partner Benefits
🎯 Early access to beta releases
📈 Co-marketing opportunities  
🤝 Technical collaboration sessions

Questions? Contact your partner manager or {PARTNER_SUPPORT_EMAIL}

Best regards,
Camunda Partner Team
```

---

## Template Usage Guidelines

### Customization Instructions

1. **Replace Placeholders:** All templates contain placeholders in `{BRACKETS}` that should be replaced with actual values.

2. **Adapt Tone:** Adjust the tone based on your audience:
   - Internal teams: More casual, technical
   - Executives: Business-focused, concise
   - Customers: Professional, benefit-focused

3. **Timing:** Send communications at optimal times:
   - Freeze announcements: 2-4 weeks in advance
   - Reminders: 24-48 hours before events
   - Emergency communications: Immediately

4. **Channels:** Use appropriate channels for each audience:
   - Engineering teams: Slack + Email
   - Executives: Email + Calendar
   - Customers: Portal + Newsletter

### Template Maintenance

- Review templates quarterly
- Update based on retrospective feedback
- Version control template changes
- Keep templates in sync with process changes

For questions about these templates, contact @camunda/monorepo-devops-team or create an issue in the repository.
