# /review-code — review AI-generated code before accepting

Checklist review of the current diff (or named files) against `rules/java-springboot.md` and `rules/testing.md`. Run before accepting agent output or merging.

## Procedure

1. Get the change set: `git diff` (unstaged), `git diff --staged`, or the files under review. Review only what changed — do not flag pre-existing code outside the diff.
2. Read `rules/java-springboot.md` and `rules/testing.md` if not already in context.
3. Check every item below. For each violation, record: file:line, the rule broken, and the fix.
4. End with a verdict (format at the bottom). Any ❌ blocks acceptance.

## 1. Package structure & layering (`rules/java-springboot.md`)

- [ ] New code sits in the feature-first layout: `ticket/{api,application,domain,dto,mapper,infrastructure}` (or a sibling feature with the same layers). No flat packages, no root-level `controller/`/`service/`.
- [ ] One public type per file; class name matches its layer (`*Controller`, `*Service`, `*Repository`).
- [ ] Controllers are thin: HTTP mapping, `@Valid`, call service, `@ResponseStatus` only. No business rules, no `@Transactional`, no repository injection, no entities in/out.
- [ ] Services own use cases: `@Transactional(readOnly = true)` on the class, `@Transactional` on write methods. No `HttpServlet*`, no status codes.
- [ ] Repositories are persistence only: no DTO construction, no business branching.
- [ ] Workflow rules live on the entity (`transitionTo`, `updateDetails`), not as if-else in the service.
- [ ] Constructor injection everywhere; no field `@Autowired`.
- [ ] Routes stay on `/api/v1/...` resource paths.

## 2. Exception handling

- [ ] Failures throw domain exceptions extending `AppException` with a stable `ErrorCode` — never try/catch-and-return error maps in controllers.
- [ ] All handling is in `GlobalExceptionHandler` as `ProblemDetail` with `title`, `detail`, and `code` set.
- [ ] Status mapping is correct: not found → 404, validation/malformed → 400, state conflict → 409, unexpected → 500 with a generic message.
- [ ] No SQL, stack traces, or exception class names leak into responses.
- [ ] New client-visible failure modes add a new `ErrorCode` (not reused `INTERNAL_ERROR`).

## 3. DTO vs entity

- [ ] No `@Entity` type appears as `@RequestBody` or a controller return type.
- [ ] Request/response types are records in `ticket.dto`; mapping goes through `TicketMapper` from the service.
- [ ] Validation annotations (`@NotBlank`, `@Size`, `@NotNull`) are on request DTOs, not entities.
- [ ] Responses nest ids or small records (`CommentResponse`), never full related entities or lazy collections.

## 4. Unit tests (`rules/testing.md`)

- [ ] Behavior changes come with tests; tests mirror the main package tree and use behavior-describing names.
- [ ] Unit tests use `@ExtendWith(MockitoExtension.class)` — no `@SpringBootTest`, no `@MockBean`.
- [ ] Only collaborators are mocked; entities/DTOs are constructed; the class under test is never mocked. Real `TicketMapper` preferred over a mock.
- [ ] Stubs match the repository methods production actually calls (`findByIdWithComments`, not `findById`).
- [ ] Assertions are AssertJ; outcomes are verified, not incidental interactions (`verify` only when the side effect is the contract).

## 5. Integration tests

- [ ] Repository/API tests run on PostgreSQL via Testcontainers (`@Container` + `@ServiceConnection` on a `static` field) — never H2.
- [ ] Tests clean up after themselves; no dependence on execution order.

## 6. State-machine coverage (mandatory when transitions change)

- [ ] Every `(from, to)` pair of `TicketStatus` — including self-pairs — is in `validTransitions()` or `invalidTransitions()`.
- [ ] Valid: status applied on response and entity. Invalid: `TicketStateConflictException`, status unchanged, no `save`.
- [ ] `transitionMatrix_isExhaustive` still passes; any `TicketStatus` change updated the sources in the same diff.

## 7. General

- [ ] No hardcoded secrets, credentials, or endpoints; no disabled validation or permissive CORS changes.
- [ ] Diff is minimal: no unrelated refactors, reformatting, or drive-by changes.
- [ ] New dependencies are justified and pinned.

## Verdict format

```
Verdict: ACCEPT | ACCEPT WITH NITS | REJECT
Blocking violations (❌):
  - file:line — rule — required fix
Nits (⚠️, non-blocking):
  - file:line — suggestion
Not run: tests / lint (state why)
```

- **REJECT** if any item above fails. **ACCEPT WITH NITS** only for style issues that break no rule. **ACCEPT** when every applicable item passes.
- When rejecting, show the fix as code the author can apply; do not just describe it.
