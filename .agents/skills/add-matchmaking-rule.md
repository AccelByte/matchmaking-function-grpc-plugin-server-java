---
name: add-matchmaking-rule
description: Add a new matchmaking rule to the custom matching logic. Handles rule implementation, integration with the matching function, and test setup.
user-invocable: true
allowed-tools: Bash, Read, Glob, Grep, Edit, Write
argument-hint: <rule description, e.g. "prefer players within 200 MMR of each other">
---

# Add Matchmaking Rule

Add a new matchmaking rule to this Extend Override app. Implements the rule logic, integrates it with the matching function, and adds test coverage.

## Arguments

`$ARGUMENTS`

Parse for:
- **Rule description**: What the matching rule does (required)
- Specific parameters (thresholds, weights, player attributes) if provided

## Context

Matchmaking Override apps use **server-side streaming** — AGS sends a stream of match tickets and expects the app to return groupings of matched players. The matching logic runs on each batch of tickets.

Key concepts:
- **Match ticket**: A player (or party) waiting for a match, with attributes like MMR, region, latency
- **Match function**: Processes a batch of tickets and returns proposed matches
- **Rules**: Constraints that determine whether tickets are compatible

## Process

### Step 1: Understand existing rules
Read the service implementation to understand:
- How the match function processes ticket batches
- How existing rules/constraints are applied
- How player attributes are accessed from ticket data
- The streaming protocol (how results are sent back)

### Step 2: Design the rule
Based on the user's description, propose:
- What player attributes to compare
- The compatibility threshold or formula
- How the rule interacts with existing rules (AND/OR logic)
- Whether it's a hard constraint (must match) or soft preference (weighted)

Show the user the design and ask for confirmation.

### Step 3: Implement the rule
Add the rule logic following existing patterns:
- Match the code style of existing rules
- Handle missing attributes gracefully (default values, skip if absent)
- Consider performance — rules run on every ticket pair in a batch

### Step 4: Integrate with match function
Wire the new rule into the matching pipeline. Follow how existing rules are checked during the matching loop.

### Step 5: Add test case
Create tests covering:
- Two compatible tickets (should match)
- Two incompatible tickets (should not match)
- Edge cases: missing attributes, boundary values, empty batches

### Step 6: Verify
- Build the project
- Run all tests
- Show the user the complete diff

## Important

- **Don't modify the proto file** — this is an Override app, proto is AccelByte-provided
- **Respect the streaming protocol** — don't break the request/response flow
- **Consider performance** — matchmaking rules run frequently on potentially large ticket pools
- **Handle missing data gracefully** — not all tickets will have every attribute
