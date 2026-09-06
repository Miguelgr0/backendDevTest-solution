# Similar Products API

Implementación reactiva en Spring Boot de la prueba técnica de backend. Expone los detalles de los
productos más similares a uno dado, preservando el orden de similitud devuelto por el servicio de
productos existente.

> Este documento es una traducción al español de [`readme.md`](readme.md), que es la referencia
> principal del proyecto. Ante cualquier discrepancia, prevalece la versión en inglés.

## Requisitos

- Docker y Docker Compose para los mocks y el test k6 proporcionados
- Java 25 (última versión LTS) y Maven 3.9+ para compilar en local, o únicamente Docker usando el
  [`Dockerfile`](Dockerfile), que compila y ejecuta el servicio sin necesidad de un JDK local

## Ejecución local

Arranca el mock de la API de productos y, opcionalmente, los paneles de rendimiento:

```bash
docker compose up -d simulado influxdb grafana
```

Comprueba que el mock está disponible:

```bash
curl http://localhost:3001/product/1/similarids
```

Ejecuta la aplicación:

```bash
mvn spring-boot:run
```

La API escucha en el puerto `5000`:

```bash
curl http://localhost:5000/product/1/similar
```

Si lo prefieres, genera el jar ejecutable y arráncalo directamente:

```bash
mvn clean package
java -jar target/similar-products-1.0.0.jar
```

O compílalo y ejecútalo en un contenedor, sin necesidad de JDK ni Maven locales:

```bash
docker build -t similar-products .
docker run --rm -p 5000:5000 similar-products
```

## Tests

Ejecuta la suite completa de tests unitarios y de integración:

```bash
mvn clean verify
```

Con la aplicación y la infraestructura proporcionada en marcha, ejecuta el test de carga original:

```bash
docker compose run --rm k6 run scripts/test.js
```

Su dashboard de Grafana está disponible en
[`http://localhost:3000/d/Le2Ku9NMk/k6-performance-test`](http://localhost:3000/d/Le2Ku9NMk/k6-performance-test).

## Contrato de la API

```http
GET /product/{productId}/similar
```

Una respuesta correcta es un array JSON ordenado:

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

Los contratos originales se mantienen en [`existingApis.yaml`](existingApis.yaml) y
[`similarProducts.yaml`](similarProducts.yaml).

## Arquitectura

La aplicación utiliza una arquitectura hexagonal deliberadamente sencilla:

```text
Adaptador REST de entrada
        |
        v
GetSimilarProductsUseCase (puerto de entrada)
        |
        v
GetSimilarProductsService
        |
        v
ProductProviderPort (puerto de salida)
        |
        v
Adaptador de caché -> Adaptador HTTP -> API externa de productos
```

- `domain`: modelo `Product` inmutable, sin dependencias de Spring ni de HTTP.
- `application`: puertos de entrada/salida, excepciones neutrales respecto al proveedor y
  orquestación del caso de uso.
- `infrastructure.adapter.in.rest`: el controlador WebFlux (mínimo) y la traducción de errores HTTP.
- `infrastructure.adapter.out.http`: comunicación vía WebClient y mapeo DTO → dominio.
- `infrastructure.adapter.out.cache`: caché Caffeine y coalescencia de peticiones concurrentes.
- `infrastructure.config`: configuración tipada y cableado de dependencias.

El servicio de aplicación depende únicamente de `ProductProviderPort`. La configuración de Spring
decide qué implementaciones de HTTP y caché se usan, de modo que la infraestructura puede sustituirse
sin modificar el caso de uso.

## Concurrencia y orden

Tras obtener los IDs similares, los detalles de los productos se solicitan mediante el operador
Reactor `flatMapSequential`. Se suscribe de forma concurrente a las peticiones de detalle
independientes hasta el límite configurado, pero emite los productos obtenidos con éxito en el orden
original de similitud. No hay ninguna llamada bloqueante, suscripción manual ni pool de hilos
auxiliar en el flujo de la petición.

La concurrencia por defecto es de ocho. Los mocks actuales devuelven tres IDs, pero el límite más
alto también soporta listas más grandes sin generar una carga descontrolada hacia el proveedor.

## Política de errores

La llamada inicial a `similarids` define la operación y, por tanto, hace fallar la petición cuando no
puede completarse:

| Resultado del proveedor | Resultado de la API |
|---|---|
| `404` | `404 Not Found` |
| timeout | `504 Gateway Timeout` |
| `5xx`, error de conexión o respuesta inválida | `502 Bad Gateway` |

Una llamada de detalle de producto es un enriquecimiento independiente. Su `404`, `5xx`, timeout,
error de conexión o respuesta inválida se omite y el resto de productos continúan. Esto proporciona
resultados parciales útiles y coincide con los escenarios de fallo explícitos de los mocks
proporcionados.

Los fallos parciales esperados se registran en nivel debug sin stacktrace. Los fallos del proveedor
principal se registran una única vez en el manejador de errores REST, con contexto de la operación.

## Timeouts

Reactor Netty tiene configurados explícitamente timeouts de conexión y de respuesta/lectura. Los
valores por defecto son de uno y ocho segundos respectivamente. Ocho segundos permite que el mock
válido de cinco segundos se complete, a la vez que corta la respuesta deliberadamente patológica de
50 segundos. Ambos valores pueden modificarse sin recompilar.

No se utiliza ningún retry automático. Reintentar el 404, el 500 o el retraso de 50 segundos
deterministas del test de carga amplificaría el tráfico y la latencia. Una política de retry acotada
solo tendría sentido con evidencia real sobre fallos transitorios en producción.

## Caché

Solo los detalles de producto obtenidos con éxito se almacenan en una caché Caffeine acotada, con
cinco minutos de vigencia. Las listas de IDs similares, los errores, los timeouts y los productos no
encontrados no se cachean. Un registro reactivo de peticiones en curso por clave coalesce los fallos
de caché simultáneos, evitando que cientos de peticiones para el mismo producto generen cientos de
llamadas al proveedor. La entrada en curso se elimina tanto en caso de éxito como de fallo.

La caché es intencionadamente local: es rápida, acotada y suficiente para este test de un solo
proceso. Redis añadiría infraestructura y nuevos modos de fallo operacional sin mejorar el resultado
requerido. En un despliegue de producción con varias instancias, podría evaluarse una caché
distribuida o invalidación basada en eventos según los requisitos de consistencia.

## Observabilidad

Actuator expone únicamente `health` y `metrics`. La caché Caffeine está enlazada con Micrometer, de
modo que su eficacia se puede medir en lugar de darse por supuesta:

```bash
curl http://localhost:5000/actuator/health
curl "http://localhost:5000/actuator/metrics/cache.gets?tag=cache:products&tag=result:hit"
curl "http://localhost:5000/actuator/metrics/cache.size?tag=cache:products"
```

## Rendimiento medido

Resultados del script k6 proporcionado, sin modificar, contra este servicio sobre JDK 25 con la
caché fría y usando los mocks incluidos (5 escenarios, 200 VUs cada uno):

| Métrica | Valor |
|---|---|
| Peticiones | 13 698 (204 req/s) |
| Latencia mediana | 13,4 ms |
| Latencia p90 | 121 ms |
| Latencia p95 | 845 ms |
| Latencia máxima | 8,04 s |

El máximo coincide con el timeout de respuesta configurado de 8 segundos: el producto
deliberadamente patológico de 50 segundos se corta en lugar de mantener la petición abierta. Los
percentiles altos están dominados por los escenarios `slow` y `verySlow`, cuyos mocks retrasan 1 y 5
segundos por diseño.

Los contadores de caché tras esa ejecución mostraron **31 959 aciertos frente a 9 138 fallos** y solo
**6 entradas cacheadas**, exactamente los productos que se resolvieron con éxito. Los productos que
devuelven 404, 500 o que expiran nunca se cachean, y los fallos restantes son precisamente las
consultas repetidas de esos productos no cacheables. El servicio no emitió ningún warning ni error de
aplicación durante toda la ejecución.

## Configuración

Los valores por defecto están en `src/main/resources/application.yml` y pueden sobrescribirse con
variables de entorno:

| Parámetro | Variable de entorno | Valor por defecto |
|---|---|---|
| Puerto del servidor | `SERVER_PORT` | `5000` |
| URL base externa | `EXTERNAL_API_BASE_URL` | `http://localhost:3001` |
| Timeout de conexión | `EXTERNAL_API_CONNECT_TIMEOUT` | `1s` |
| Timeout de respuesta/lectura | `EXTERNAL_API_RESPONSE_TIMEOUT` | `8s` |
| Concurrencia de detalle | `SIMILAR_PRODUCTS_MAX_CONCURRENCY` | `8` |
| Tamaño máximo de caché | `PRODUCT_CACHE_MAXIMUM_SIZE` | `1000` |
| TTL de caché | `PRODUCT_CACHE_TTL` | `5m` |

Spring Boot ya mapea `SERVER_PORT`; el resto de variables se declaran explícitamente. La
configuración tipada rechaza tamaños, concurrencia, URLs, TTLs y timeouts inválidos durante el
arranque.

## Cobertura de tests

La suite automática cubre:

- agregación correcta, resultados vacíos, orden bajo finalización desordenada y concurrencia máxima;
- manejo parcial de `404`, `500` y timeout, además de la propagación del fallo de la consulta inicial;
- mapeo JSON de WebClient, IDs numéricos/textuales, clasificación de status, datos inválidos y
  timeout real;
- aciertos de caché, expiración por TTL, no cacheo de errores y comportamiento single-flight;
- status REST, tipo de contenido, estructura JSON y mapeo de errores;
- una petición completa a través de Spring Boot: controller, servicio, caché, WebClient y un
  MockWebServer;
- las propias fronteras de la arquitectura, mediante reglas ArchUnit que rompen el build si el
  dominio adquiere una dependencia fuera del JDK, o si Spring, WebClient o Caffeine se filtran fuera
  de infraestructura.

## Trade-offs y evolución hacia producción

No hay base de datos porque el servicio no posee estado persistente. No hay circuit breaker: los
timeouts, la concurrencia acotada, el manejo de resultados parciales y la coalescencia de peticiones
cubren los modos de fallo demostrados con menos estado y menos ceremonia.

Para producción, las siguientes incorporaciones serían métricas de latencia/caché con Micrometer,
trazabilidad distribuida, logs estructurados y alertas de saturación del proveedor. Un circuit
breaker, bulkhead, rate limiting o una caché distribuida deberían introducirse solo cuando los datos
de tráfico y fallos establezcan esa necesidad.
