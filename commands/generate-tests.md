# /generate-tests — generate JUnit tests for a class

Generate tests for a given class, **prioritizing edge cases and invalid-input paths** over happy paths. Follow `rules/testing.md` exactly — read it first.

## Procedure

1. **Target** — the class the user names, or the open file. Read it fully, plus everything it touches: entities, DTOs, repositories, mappers, exceptions.
2. **Existing coverage** — read the matching `*Test` / `*IT` file if one exists. Add only missing cases; never duplicate or rewrite passing tests.
3. **Behavior inventory** — before writing any test, list in the response: for each public method, every branch, every exception thrown, every validation rule, every boundary. Mark each as `covered`, `adding`, or `skipped` (with reason).
4. **Generate in priority order** (below). Happy paths come last, not first.
5. **Verify** — run the new tests if a build file exists (`./mvnw test -Dtest=...` or Gradle equivalent). If there is no build file or the run fails for environmental reasons, state clearly that tests were not run and why.

## Priority order

1. **Invalid input** — null, blank, whitespace-only, over-limit strings (boundary + 1), invalid enum values, negative/zero numbers. Assert the validation or domain exception, not just "an exception".
2. **Not found / empty** — unknown ids (`TicketNotFoundException`), empty repository results, empty comment lists, null optional fields (`assignee`).
3. **State conflicts** — invalid transitions throw `TicketStateConflictException` and leave status unchanged; transitions out of terminal states (`CLOSED`, `CANCELLED`); self-transitions.
4. **Boundaries** — exactly max length vs max+1; empty vs singleton collections; first/last elements.
5. **Happy path** — one per public method, only after the above are covered.
6. **Side effects** — `save` called or `never()` called, `updatedAt` bumped, comment attached to the right ticket.

## Conventions (from `rules/testing.md`)

- Unit tests: `@ExtendWith(MockitoExtension.class)`. **No** `@SpringBootTest`, no `@MockBean`. Mock collaborators only; construct entities and DTOs directly; prefer a real `TicketMapper`; wire in `@BeforeEach` when `@InjectMocks` can't mix mocks and real objects.
- Names state behavior: `transition_whenInvalid_throwsConflictAndKeepsStatus`.
- Arrange / Act / Assert separated by blank lines. AssertJ only (`assertThat`, `assertThatThrownBy`).
- `@ParameterizedTest` + `@MethodSource` for input matrices instead of copy-pasted tests.
- Stub the exact repository methods production calls (`findByIdWithComments`, not `findById`).
- `verify` only when the side effect *is* the contract (e.g. `verify(ticketRepository, never()).save(any())` on a rejected transition).
- If the class touches the status state machine, the mandatory matrix applies: every `(from, to)` pair valid + invalid, plus the exhaustiveness test — see `rules/testing.md` §State-machine transition coverage.
- Repository/API tests (Testcontainers PostgreSQL, `IT` suffix) only when the user asks or the class is a repository/controller; default output is unit tests.
- Do not test private methods directly; do not mock the class under test; one behavior per test.

## Output format

```
Behavior inventory for <Class>:
  - create: blank title → validation error [adding]; missing id → ... [covered by TicketServiceTest]
  - ...
New tests: <file path>
Run: <command + result, or "not run — no build file present">
Gaps: <anything that needs Testcontainers, a build file, or a spec decision>
```
