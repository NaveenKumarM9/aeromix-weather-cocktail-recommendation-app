# Aeromix — Weather-Aware Cocktail Recommendations

Aeromix is a full-stack Java application that recommends cocktails for the current weather in a selected city. A user can search for a location, view live conditions, filter by spirit or alcohol preference, open complete recipes, and save favorite drinks in the browser.

The recommendation flow combines:

- Live weather and geocoding responses retrieved as JSON
- Editorial weather-pairing rules stored in JSON
- Seasonal scoring adjustments stored in XML
- Cocktail recipes and ingredient data queried from SQL
- A responsive browser interface served by the Java application

The project demonstrates Java, Spring Boot, REST APIs, JPA/Hibernate, SQL, JSON/XML processing, automated testing, Docker, CI/CD, security controls, monitoring, and cloud deployment configuration.

## Local runtime requirements

| Component | Required version | Local setup |
| --- | --- | --- |
| Operating system | Windows 10 or 11 | Required for the supplied PowerShell launcher |
| Java Development Kit | Java 25 LTS (`25.x`) | Installed automatically inside this project by `run-local.ps1` |
| Maven | Maven 3.9.16 | Included through `mvnw.cmd`; do not install Maven separately |
| Spring Boot | 4.1.0 | Downloaded by Maven during the first build |
| Local database | H2 2.4.240 | Embedded in the application; no database server is needed |
| Browser | Current Chrome, Edge, or Firefox | Used to access the application |
| Internet connection | Required on the first run and for live weather | Downloads the runtime/dependencies and contacts Open-Meteo |
| Weather API key | Not required for local non-commercial evaluation | The public Open-Meteo endpoints are used by default |

The first startup takes longer because Java and Maven dependencies must be downloaded. Later startups reuse the downloaded files.

## Recommended VS Code extensions

Install these extensions from the VS Code Extensions view (`Ctrl+Shift+X`):

1. **Extension Pack for Java** by Microsoft (`vscjava.vscode-java-pack`)
2. **Spring Boot Extension Pack** by VMware (`vmware.vscode-boot-dev-pack`)

Docker and YAML extensions are optional and are only needed when working with container or deployment files.

## Run locally from VS Code

1. Open VS Code.
2. Select **File → Open Folder**.
3. Open `C:\Users\navee\Desktop\CocktailWeather`.
4. Select **Terminal → New Terminal**.
5. Run:

```powershell
.\run-local.ps1
```

6. Wait until the terminal displays a message similar to:

```text
Started CocktailWeatherApplication
```

7. Open [http://localhost:8080](http://localhost:8080).

Keep the terminal open while using the application. To stop it, click inside the terminal and press `Ctrl+C`.

## Run by double-clicking

Open the project folder in File Explorer and double-click:

```text
run-local.cmd
```

Keep the command window open, wait for the application to start, and then visit [http://localhost:8080](http://localhost:8080).

## How the project-specific Java runtime works

The project requires Java 25, but Java 25 does not need to replace the Java version used by the rest of the computer.

When `run-local.ps1` is executed, it:

1. Finds the folder containing the script and treats it as the project root.
2. Checks the currently available Java version.
3. Uses the system Java when it is a compatible Java 25 release.
4. Otherwise downloads a checksum-verified Java 25 runtime into `.runtime\jdk-25...`.
5. Temporarily sets `JAVA_HOME` and `PATH` for the launcher process.
6. Starts this project with `.\mvnw.cmd spring-boot:run`.

This mapping applies only while the launcher is running. It does not change the computer’s global `JAVA_HOME`, and it does not affect other Java projects.

Always use the launcher when the system Java is older than Java 25:

```powershell
.\run-local.ps1
```

Running `.\mvnw.cmd spring-boot:run` directly uses the Java configured in the current terminal. It will fail with `RequireJavaVersion` if that terminal is using Java 23 or another unsupported version.

## Local application behavior

The default local configuration:

- Runs the website and API on port `8080`
- Stores local database data under the project’s `data\` directory
- Automatically applies the Flyway SQL migration
- Uses the public Open-Meteo weather and geocoding endpoints
- Does not require PostgreSQL, Docker, or an API key
- Preserves saved browser favorites in the browser’s local storage

The following generated directories are intentionally excluded from Git:

| Directory | Purpose |
| --- | --- |
| `.runtime\` | Project-local Java 25 download and temporary runtime files |
| `target\` | Compiled classes, reports, and packaged application |
| `data\` | Persistent local H2 database files |

## Common startup problems

### `Detected JDK ... version 23 ... is not in the allowed range [25,26)`

The Maven wrapper was started directly with Java 23. Run the project launcher instead:

```powershell
.\run-local.ps1
```

### `mvnw.cmd is not recognized`

PowerShell does not execute files from the current directory without a path prefix. Use:

```powershell
.\mvnw.cmd
```

For normal local startup, prefer `.\run-local.ps1`.

### Port 8080 is already in use

Another application instance is still running. Return to its terminal and press `Ctrl+C`, then start Aeromix again.

### The first run appears slow

Allow the Java 25 and Maven downloads to complete. Do not close the terminal during the one-time setup. Subsequent starts are much faster.

### Weather data is unavailable

Confirm that the computer has internet access. The application can provide a short estimated fallback during a temporary weather-provider failure, but location search requires access to Open-Meteo.

## Production-style run

With Docker installed:

```bash
docker compose up --build
```

This starts the application with PostgreSQL 18.4, runs Flyway migrations, and exposes the site on port 8080. Set a strong `POSTGRES_PASSWORD` before any shared deployment.

The production profile moves health and metrics to the internal management port 8081. Do not expose that port publicly. For Kubernetes, create the referenced `cocktail-weather-database` Secret through your deployment platform rather than committing credentials:

```bash
kubectl create secret generic cocktail-weather-database \
  --from-literal=url='jdbc:postgresql://postgres:5432/cocktail_weather' \
  --from-literal=username='cocktail' \
  --from-literal=password='replace-with-a-strong-secret'
```

## How the recommendation pipeline works

1. The browser calls `GET /api/v1/recommendations` with a city and optional preferences.
2. `OpenMeteoWeatherClient` geocodes the city and retrieves current conditions, with a ten-minute in-memory cache and a short safe fallback if the provider is temporarily unavailable.
3. `PairingRuleCatalog` loads weather-to-flavor mappings from `weather-pairing-rules.json`.
4. `SeasonalOverrideCatalog` securely parses `seasonal-overrides.xml` with external entities disabled.
5. `CocktailRepository` executes a native SQL query against the catalog to select temperature-appropriate candidates.
6. `RecommendationService` scores candidates using temperature fit, weather-flavor tags, and the active seasonal boost.

## API

| Endpoint | Purpose |
| --- | --- |
| `GET /api/v1/recommendations?city=Chicago&limit=6` | Weather and ranked recommendations |
| `GET /api/v1/recommendations?city=Miami&spirit=rum&alcoholic=true` | Filtered recommendations |
| `GET /api/v1/locations?query=Chic` | City autocomplete |
| `GET /api/v1/cocktails` | Full SQL-backed catalog |
| `GET /api/v1/cocktails/{slug}` | One recipe |
| `GET :8081/actuator/health` | Internal production health |
| `GET :8081/actuator/health/liveness` | Container liveness |
| `GET :8081/actuator/health/readiness` | Container readiness |
| `GET :8081/actuator/prometheus` | Internal Prometheus metrics |

Errors use RFC 9457 Problem Details responses. Public API traffic is guarded by a lightweight per-client request limit.

## Production features

- Spring Boot 4.1.0 on Java 25 LTS
- Maven Wrapper 3.9.16 and reproducible CI build
- PostgreSQL 18.4 production profile and persistent H2 local profile
- Flyway-managed schema, constraints, indexes, and curated seed data
- Live Open-Meteo weather and geocoding with timeouts, caching, and graceful fallback
- REST validation and Problem Details error responses
- Content Security Policy, clickjacking protection, MIME-sniffing protection, and restrictive browser permissions
- Actuator health, Kubernetes probes, metrics, and Prometheus output
- Responsive, keyboard-friendly frontend with no third-party runtime JavaScript
- Device-local saved recipes and zero-proof filtering
- JUnit integration tests covering SQL, JSON, XML, REST, and recommendation behavior
- Multi-stage non-root Docker image, Docker Compose, Kubernetes manifest, and GitHub Actions

## Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `PORT` | `8080` | HTTP port |
| `DEFAULT_CITY` | `Chicago` | Default startup city |
| `DATABASE_URL` | local H2 URL | JDBC connection URL |
| `DATABASE_USERNAME` | `sa` | Database user |
| `DATABASE_PASSWORD` | empty | Database password |
| `DB_POOL_SIZE` | `10` | Maximum connection pool size |
| `WEATHER_CACHE_MINUTES` | `10` | Live weather cache duration |
| `WEATHER_API_KEY` | empty | Optional paid Open-Meteo customer API key |
| `WEATHER_FORECAST_URL` | public Open-Meteo endpoint | Forecast endpoint; use the customer endpoint with a commercial plan |
| `WEATHER_GEOCODING_URL` | public Open-Meteo endpoint | Geocoding endpoint; use the customer endpoint with a commercial plan |
| `MANAGEMENT_PORT` | `8081` in production | Internal health and metrics port |

Open-Meteo's free public API does not require a key, but it is intended for non-commercial use and has usage limits. A public commercial deployment should use an appropriate paid plan, set `WEATHER_API_KEY`, and use the customer API endpoints.

## Verification

```powershell
.\mvnw.cmd clean verify
```

The release verification currently includes:

- 23 automated tests covering the complete Spring context, Flyway migration, 14-recipe SQL catalog, JSON pairing rules, XML seasonal data, native queries, REST validation, rate limiting, provider failures, and security headers
- 94.5% instruction coverage and 66.7% branch coverage, with enforced JaCoCo minimums
- SpotBugs static analysis with zero findings
- A CycloneDX software bill of materials containing 107 components
- An OSV dependency scan with zero known vulnerability matches
- Desktop and mobile browser checks for recommendations, filters, autocomplete, recipe dialogs, saved recipes, error states, accessibility state, responsive layout, and browser console errors
- A packaged production-profile smoke test with public Actuator endpoints blocked and health/readiness available only on the internal management port

GitHub Actions repeats the build and tests, scans the generated SBOM with the official OSV workflow, and performs an additional production-profile smoke test against PostgreSQL 18.4. Dependabot is configured for Maven and GitHub Actions updates.

## Data and technical sources

- [Open-Meteo Forecast API](https://open-meteo.com/en/docs)
- [Open-Meteo Geocoding API](https://open-meteo.com/en/docs/geocoding-api)
- [Spring Boot 4.1 system requirements](https://docs.spring.io/spring-boot/system-requirements.html)
- [Spring Boot production-ready features](https://docs.spring.io/spring-boot/reference/actuator/index.html)
- [OWASP HTTP Security Response Headers guidance](https://cheatsheetseries.owasp.org/cheatsheets/HTTP_Headers_Cheat_Sheet.html)
- [OpenJDK 25](https://openjdk.org/projects/jdk/25/)

The cocktail recipes are original project seed content, intended for demonstration. Measurements and alcohol content should be reviewed before commercial hospitality use.
