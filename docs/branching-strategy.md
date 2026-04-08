# Branch Strategy

This repository uses a lightweight trunk-based flow with short-lived branches.

## Default Branch

- `main` is always releasable.
- Direct commits to `main` are discouraged.

## Branch Naming

Use one of these prefixes:

- `feature/<short-description>` for new functionality
- `fix/<short-description>` for bug fixes
- `chore/<short-description>` for tooling/build/maintenance
- `docs/<short-description>` for documentation-only changes
- `release/<version-or-date>` for release prep work

Examples:

- `feature/mockdevice-smoke-test`
- `fix/sdkmanager-detection`
- `docs/setup-clarifications`

## Workflow

1. Branch from latest `main`.
1. Keep branch focused on one topic.
1. Open PR early (draft is fine).
1. Merge with squash to keep `main` history clean.

## Pull Request Expectations

- PR title: concise and action-oriented.
- Include testing evidence (commands and outcomes).
- Call out any config or secret changes explicitly.

## Suggested Protection Rules (when remote is created)

- Require pull request before merging.
- Require 1 approval.
- Require status checks (CI build + tests).
- Block force pushes to `main`.
