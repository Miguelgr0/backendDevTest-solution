# Similar Products API

Reactive Spring Boot implementation of the backend technical test. It exposes the product details
most similar to a requested product while preserving the similarity order returned by the existing
product service.

> A Spanish translation is available at [`readme.es.md`](readme.es.md). This English version is the
> canonical reference.

## Requirements

- Docker and Docker Compose for the supplied mocks and k6 test
- Java 25 (latest LTS release) and Maven 3.9+ to build locally, or just Docker using the
  [`Dockerfile`](Dockerfile), which compiles and runs the service without a local JDK

## Run locally

Start the supplied product API mock and, optionally, the performance dashboards:

```bash
docker compose up -d simulado influxdb grafana
```

Check that the mock is available:

```bash
curl http://localhost:3001/product/1/similarids
```

Run the application:

```bash
mvn spring-boot:run
```

The API listens on port `5000`:

```bash
curl http://localhost:5000/product/1/similar
```

Build the executable jar and run it directly if preferred:

```bash
mvn clean package
java -jar target/similar-products-1.0.0.jar
```

Or build and run it in a container, which needs no local JDK or Maven:

```bash
docker build -t similar-products .
docker run --rm -p 5000:5000 similar-products
```

## Tests

Run the full unit and integration suite:

```bash
mvn clean verify
```

With the application and the supplied infrastructure running, execute the original load test:

```bash
docker compose run --rm k6 run scripts/test.js
```

Its Grafana dashboard is available at
[`http://localhost:3000/d/Le2Ku9NMk/k6-performance-test`](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test).

## API contract

```http
GET /product/{productId}/similar
```

A successful response is an ordered JSON array:

```json
[
  {
    "id": "2",
    "name": "Dress",
    "price": 19.99,
    "availability": true
  }
]
```

The source contracts remain in [`existingApis.yaml`](existingApis.yaml) and
[`similarProducts.yaml`](similarProducts.yaml).

## Architecture

The application uses a deliberately small hexagonal architecture:

```text
Inbound REST adapter
        |
        v
GetSimilarProductsUseCase (input port)
        |
        v
GetSimilarProductsService
        |
        v
ProductProviderPort (output port)
        |
        v
Caching adapter -> HTTP adapter -> external product API
```

- `domain`: immutable `Product` model with no Spring or HTTP dependencies.
- `application`: input/output ports, provider-neutral exceptions, and use-case orchestration.
- `infrastructure.adapter.in.rest`: the thin WebFlux controller and HTTP error translation.
- `infrastructure.adapter.out.http`: WebClient communication and DTO-to-domain mapping.
- `infrastructure.adapter.out.cache`: Caffeine caching and concurrent request coalescing.
- `infrastructure.config`: typed configuration and dependency wiring.

The application service depends only on `ProductProviderPort`. Spring configuration chooses the
HTTP and caching implementations, so infrastructure can be replaced without modifying the use case.

## Concurrency and ordering

After retrieving the similar IDs, product details are requested through Reactor
`flatMapSequential`. It subscribes to independent detail requests concurrently up to the configured
limit, but emits successful products in the original similarity order. There is no blocking call,
manual subscription, or auxiliary thread pool in the request flow.

The default concurrency is eight. The current mocks return three IDs, while the higher ceiling also
supports larger lists without creating unbounded downstream load.

## Failure policy

The initial `similarids` call defines the operation and therefore fails the request when it cannot be
completed:

| Downstream result | API result |
|---|---|
| `404` | `404 Not Found` |
| timeout | `504 Gateway Timeout` |
| `5xx`, connection or invalid response | `502 Bad Gateway` |

A product-detail call is an independent enrichment. Its `404`, `5xx`, timeout, connection error, or
invalid response is omitted while the remaining products continue. This provides useful partial
results and matches the explicit failure scenarios in the supplied mocks.

Expected partial failures are logged at debug level without stack traces. Primary provider failures
are logged once by the REST error handler with operation context.

## Timeouts

Reactor Netty has explicit connection and response/read timeouts. Defaults are one and eight seconds
respectively. Eight seconds allows the valid five-second mock to complete while cutting off the
deliberately pathological 50-second response. Both values can be changed without recompilation.

No automatic retry is used. Retrying the load test's deterministic `404`, `500`, or 50-second delay
would amplify traffic and latency. A narrowly scoped retry policy would only be appropriate with
real evidence about transient production failures.

## Cache

Only successful product details are stored in a bounded, five-minute Caffeine cache. Similar-ID
lists, errors, timeouts, and missing products are not cached. A per-key reactive in-flight registry
coalesces simultaneous cache misses, preventing hundreds of requests for the same product from
creating hundreds of downstream calls. The in-flight entry is removed on success or failure.

The cache is intentionally local: it is fast, bounded, and sufficient for this single-process test.
Redis would add infrastructure and operational failure modes without improving the required result.
In a horizontally scaled production deployment, a distributed cache or event-driven invalidation
could be evaluated based on consistency requirements.

## Observability

Actuator exposes `health` and `metrics` only. The Caffeine cache is bound to Micrometer, so its
effectiveness is measurable rather than assumed:

```bash
curl http://localhost:5000/actuator/health
curl "http://localhost:5000/actuator/metrics/cache.gets?tag=cache:products&tag=result:hit"
curl "http://localhost:5000/actuator/metrics/cache.size?tag=cache:products"
```

## Measured performance

Results of the supplied k6 script, unmodified, against this service on JDK 25 with a cold cache,
using the provided mocks (5 scenarios, 200 VUs each):

| Metric | Value |
|---|---|
| Requests | 13 698 (204 req/s) |
| Median latency | 13.4 ms |
| p90 latency | 121 ms |
| p95 latency | 845 ms |
| Max latency | 8.04 s |

The maximum tracks the configured 8-second response timeout: the deliberately pathological
50-second product is cut off rather than allowed to hold the request open. The tail percentiles are
dominated by the `slow` and `verySlow` scenarios, whose mocks delay 1 and 5 seconds by design.

Cache counters after that run showed **31 959 hits against 9 138 misses** and only **6 cached
entries** — precisely the products that resolved successfully. The 404, 500 and timing-out products
were never cached, and the remaining misses are the repeated lookups of those uncacheable products.
The service emitted no application warnings or errors during the whole run.

## Configuration

Defaults live in `src/main/resources/application.yml` and can be overridden with environment
variables:

| Setting | Environment variable | Default |
|---|---|---|
| Server port | `SERVER_PORT` | `5000` |
| External base URL | `EXTERNAL_API_BASE_URL` | `http://localhost:3001` |
| Connection timeout | `EXTERNAL_API_CONNECT_TIMEOUT` | `1s` |
| Response/read timeout | `EXTERNAL_API_RESPONSE_TIMEOUT` | `8s` |
| Detail concurrency | `SIMILAR_PRODUCTS_MAX_CONCURRENCY` | `8` |
| Cache maximum size | `PRODUCT_CACHE_MAXIMUM_SIZE` | `1000` |
| Cache TTL | `PRODUCT_CACHE_TTL` | `5m` |

Spring Boot already maps `SERVER_PORT`; the remaining variables are declared explicitly. Typed
configuration rejects invalid sizes, concurrency, URLs, TTLs, and timeouts during startup.

## Test coverage

The automated suite covers:

- correct aggregation, empty results, order under out-of-order completion, and maximum concurrency;
- partial `404`, `500`, and timeout handling, plus propagation of the initial lookup failure;
- WebClient JSON mapping, numeric/string IDs, status classification, invalid data, and real timeout;
- successful cache hits, TTL expiry, non-caching of errors, and single-flight behavior;
- REST status, media type, JSON structure, and error mapping;
- a full Spring Boot request through controller, service, cache, WebClient, and a MockWebServer;
- the architecture boundaries themselves, via ArchUnit rules that fail the build if the domain gains
  a dependency outside the JDK, or if Spring, WebClient or Caffeine leak out of infrastructure.

## Trade-offs and production evolution

There is no database because the service owns no persistent state. There is no circuit breaker:
timeouts, bounded concurrency, partial-result handling, and request coalescing cover the demonstrated
failure modes with less state and ceremony.

For production, the next additions would be Micrometer latency/cache metrics, distributed tracing,
structured logs, and downstream saturation alerts. A circuit breaker, bulkhead, rate limiting, or
distributed cache should be introduced only after traffic and failure data establishes the need.
