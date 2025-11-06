# Release Communication Guide

## Overview

This guide outlines the communication strategy for Camunda's monorepo release process, specifically focusing on code freeze and feature freeze periods. Clear communication ensures all engineering teams are aligned on release timelines and constraints.

## Code Freeze vs Feature Freeze

Understanding the difference between these two freeze types is crucial for effective release management:

### Code Freeze

- **Definition**: No new code changes allowed in the release branch
- **Duration**: Typically 1-2 weeks before release
- **Purpose**: Stabilize the codebase and allow comprehensive testing
- **Exceptions**: Critical bug fixes only (requires escalation approval)
- **Impact**:
  - No new features or enhancements
  - Only critical production issues can be addressed
  - All changes must be approved by release manager

### Feature Freeze

- **Definition**: No new features can be added to the upcoming release
- **Duration**: Typically 3-4 weeks before release
- **Purpose**: Allow time for feature completion, testing, and documentation
- **Exceptions**: Features already in progress may continue (at discretion of release manager)
- **Impact**:
  - New feature development should target next release
  - Focus shifts to bug fixes and stabilization
  - Documentation and testing activities increase

## Release Timeline

### Monthly Release Schedule

- **Week 1**: Feature development
- **Week 2**: Feature development continues
- **Week 3**: Feature freeze begins
- **Week 4**: Code freeze begins, release preparation

### Minor Release Schedule (8.9 and beyond)

- **8 weeks before**: Feature planning begins
- **4 weeks before**: Feature freeze
- **2 weeks before**: Code freeze
- **Release day**: Deploy to production

## Communication Protocols

### 1. Pre-Release Communications

#### Feature Freeze Announcement

- **When**: 4 weeks before minor release, 1 week before monthly release
- **Audience**: All engineering teams, product managers, QA teams
- **Method**: Slack announcement + Email + Calendar event
- **Content**: Feature cutoff date, next release target dates

#### Code Freeze Announcement

- **When**: 2 weeks before minor release, 3 days before monthly release
- **Audience**: All engineering teams, DevOps, QA teams
- **Method**: Slack announcement + Email + Calendar event
- **Content**: Change restrictions, emergency procedures, release timeline

### 2. During Freeze Period

#### Daily Reminders

- **Method**: Automated Slack bot messages
- **Content**: Current freeze status, days remaining, emergency contact info

#### Emergency Change Requests

- **Process**: Escalation through release manager
- **Documentation**: Required impact assessment and rollback plan
- **Approval**: Release manager + affected team lead

### 3. Post-Release Communications

#### Release Completion

- **When**: Within 24 hours of successful release
- **Content**: What was released, freeze period end, next release dates

## Calendar Integration

### Automated Calendar Events

All release-related dates will be automatically added to shared engineering calendars:

1. **Monthly Release Calendars**
   - Feature freeze start
   - Code freeze start
   - Release day
   - Post-release retrospective

2. **Minor Release Calendars**
   - Feature planning kick-off
   - Feature freeze start
   - Code freeze start
   - Release day
   - Post-release retrospective

### Calendar Event Details

Each calendar event will include:

- Clear description of the freeze type
- Contact information for release manager
- Links to relevant documentation
- Emergency escalation procedures

## Communication Channels

### Primary Channels

- **Slack**: `#engineering-releases` channel for all release communications
- **Email**: Engineering distribution list for formal notifications
- **Calendar**: Shared engineering calendar for important dates

### Secondary Channels

- **Teams/Project channels**: For project-specific communication
- **Documentation**: Confluence/Wiki updates
- **Dashboard**: Release status dashboard (if available)

## Templates and Examples

### Feature Freeze Announcement Template

```text
🚨 FEATURE FREEZE ANNOUNCEMENT 🚨

The feature freeze for Release X.Y.Z begins on [DATE] at [TIME].

What this means:
- No new features can be started for this release
- Focus on completing in-progress features
- Begin stabilization and testing activities

Next Important Dates:
- Code Freeze: [DATE]
- Release Date: [DATE]

Questions? Contact the release manager: @[RELEASE_MANAGER]
```

### Code Freeze Announcement Template

```text
🔒 CODE FREEZE IN EFFECT 🔒

The code freeze for Release X.Y.Z is now in effect until [DATE].

What this means:
- No code changes allowed without approval
- Only critical bug fixes will be considered
- All changes must go through emergency process

Emergency Process:
1. Contact release manager: @[RELEASE_MANAGER]
2. Provide impact assessment and rollback plan
3. Get approval before making any changes

Release Timeline:
- Code Freeze Ends: [DATE]
- Release Date: [DATE]
```

## Best Practices

### For Engineering Teams

1. **Plan Ahead**: Review release calendar monthly
2. **Communicate Early**: Inform release manager of potential late features
3. **Respect Freezes**: Follow emergency procedures for critical changes
4. **Stay Informed**: Monitor release communication channels

### For Release Managers

1. **Early Communication**: Send freeze notifications well in advance
2. **Clear Expectations**: Explain what each freeze type means
3. **Regular Updates**: Provide daily status during freeze periods
4. **Documentation**: Keep all communications and decisions documented

### For Product Managers

1. **Feature Planning**: Align feature requirements with release timeline
2. **Priority Setting**: Help determine what constitutes "critical" changes
3. **Stakeholder Updates**: Communicate release impacts to external stakeholders

## Metrics and Feedback

### Success Metrics

- Reduction in freeze violations
- Improved release stability
- Decreased time to release
- Positive team feedback on communication

### Continuous Improvement

- Post-release retrospectives
- Regular feedback collection from engineering teams
- Communication effectiveness surveys
- Process refinement based on learnings

## Contact Information

- **Release Manager**: [TBD - will be updated per release]
- **DevOps Team**: `@camunda/monorepo-devops-team`
- **Questions/Issues**: `#engineering-releases` Slack channel

---

*This document will be updated as the release process evolves. Last updated: [DATE]*
