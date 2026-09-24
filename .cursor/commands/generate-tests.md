---
description: Generate JUnit tests for a given class, prioritizing edge cases and invalid-input paths, following rules/testing.md
---

Generate JUnit tests for the class the user names (or the open file) using `commands/generate-tests.md`. Read that file and `rules/testing.md` first. Build a behavior inventory of branches, exceptions, and boundaries; check the existing test file and add only missing cases; generate tests in priority order — invalid input, not-found/empty, state conflicts, boundaries, then happy paths. Unit tests use JUnit 5 + Mockito without a Spring context; use Testcontainers only for repository/API tests. Run the new tests if a build file exists; otherwise state they were not run and why.
