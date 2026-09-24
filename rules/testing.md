# Testing (JUnit 5 + Mockito + Testcontainers)

Apply when adding or changing anything under `src/test`.

## Layout and naming

- Tests mirror the main package tree: `src/test/java/com/company/ticketmanagement/...`.
- Unit tests: `TicketServiceTest`, `TicketQaServiceTest` (no suffix). Repository/API tests: `TicketRepositoryIT`, `TicketApiIT` (suffix `IT`).
- Test method names state behavior: `create_whenValid_startsOpen`, `transition_whenInvalid_throwsConflictAndKeepsStatus`. Use `@DisplayName` only when the method name cannot.
- Arrange / Act / Assert, separated by blank lines. Assert with AssertJ (`assertThat(...)`), not JUnit `assertEquals` on objects.

## Unit tests — JUnit 5 + Mockito

- Pure unit tests: `@ExtendWith(MockitoExtension.class)` with `@Mock` collaborators. **No Spring context** — never `@SpringBootTest` or `@MockBean` in a unit test.
- Mock collaborators only (repositories, other services). Never mock the class under test. Never mock entities or DTOs — construct them directly (`new Ticket(...)`, request records).
- Prefer a real mapper (`new TicketMapper()`) over mocking mapping; wire the service in `@BeforeEach` when `@InjectMocks` cannot mix mocks and real collaborators.
- Verify outcomes (returned DTO, entity status). Use `verify` only when the side effect *is* the contract (e.g. `never().save(...)` on a rejected transition).
- Prefer `@ParameterizedTest` + `@MethodSource` over copy-pasted tests for input matrices.
- Stub the same repository methods the production code calls (`findByIdWithComments`, not `findById`, unless the service uses `findById`).

```java
// BAD: Spring context for a unit test, mocking the class under test
@SpringBootTest
class TicketServiceTest {
    @MockBean TicketService ticketService;
}

// GOOD
@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock TicketRepository ticketRepository;
    TicketMapper ticketMapper = new TicketMapper();
    TicketService ticketService;

    @BeforeEach
    void wire() {
        ticketService = new TicketService(ticketRepository, ticketMapper);
    }

    @Test
    void getById_whenMissing_throwsTicketNotFound() {
        given(ticketRepository.findByIdWithComments(7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> ticketService.getById(7L))
                .isInstanceOf(TicketNotFoundException.class);
    }
}
```

## Repository and integration tests — Testcontainers

- Repository tests (`@DataJpaTest`) and API tests (`@SpringBootTest` + `@AutoConfigureMockMvc`) run against **PostgreSQL in Testcontainers** — never H2, so queries and dialect match production.
- One container per test class: `static` field with `@Container` + `@ServiceConnection` (Spring Boot 3.1+). Test-scoped deps: `spring-boot-testcontainers` and `org.testcontainers:postgresql` — pin versions in the build file.
- Each test leaves the database clean: transactional rollback or explicit cleanup; never depend on test execution order.
- API ITs drive HTTP with MockMvc and assert status plus JSON (`jsonPath`). Cover at least one 409 invalid transition at the HTTP boundary; the full transition matrix lives in unit tests (below).

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TicketApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired MockMvc mockMvc;
}
```

```java
@DataJpaTest
@Testcontainers
class TicketRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired TicketRepository ticketRepository;
}
```

## State-machine transition coverage (mandatory)

Ticket status is a state machine (`OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `CANCELLED`) defined by `TicketStatus.allowedTransitions()`. **Every `(from, to)` pair must be tested as either valid or invalid** — including `from == to`.

1. **Valid** — `transition_whenValid_updatesStatus`: new status on the response **and** on the entity.
2. **Invalid** — `transition_whenInvalid_throwsConflictAndKeepsStatus`: `TicketStateConflictException`, status unchanged, no `save` of a mutated entity.

Enumerate with `@ParameterizedTest`, not one test per pair. Keep `validTransitions()` explicit (the allowed edges). Derive `invalidTransitions()` from `!from.canTransitionTo(to)` so new enum values fail until the matrix is updated.

```java
static Stream<Arguments> validTransitions() {
    return Stream.of(
            arguments(OPEN, IN_PROGRESS),
            arguments(OPEN, CANCELLED),
            arguments(IN_PROGRESS, RESOLVED),
            arguments(IN_PROGRESS, CANCELLED),
            arguments(RESOLVED, CLOSED));
}

@ParameterizedTest
@MethodSource("validTransitions")
void transition_whenValid_updatesStatus(TicketStatus from, TicketStatus to) { ... }

@ParameterizedTest
@MethodSource("invalidTransitions")
void transition_whenInvalid_throwsConflictAndKeepsStatus(TicketStatus from, TicketStatus to) { ... }

@Test
void transitionMatrix_isExhaustive() {
    // listed valid ∪ invalid == every (from, to) in TicketStatus, including self-pairs
}
```

- `transitionMatrix_isExhaustive` **must** fail when a pair is listed in neither source (or listed twice).
- Adding or removing a transition in `TicketStatus` **requires** updating `validTransitions()` in the same change. A state change without both parameterized tests and the completeness check is incomplete.
- Unit tests own the matrix. An API IT may sample one invalid HTTP 409; it does not replace the exhaustive unit matrix.
