# CloudSentinel AI — Implementation Plan

## Context

Build a production-grade AI-driven SRE automation platform from scratch. The system simulates cloud infrastructure failures (CPU spikes, memory leaks, cascading crashes, network partitions) and trains a Reinforcement Learning agent to detect and resolve them while optimizing for SLO adherence, downtime minimization, and cost efficiency. This is a greenfield project — the workspace is currently empty.

**User priorities:** Full stack (all 11 phases), AI/RL pipeline first, Java 21 as specified.

## Monorepo Structure

```
CloudSentinel AI/
├── backend/          # Spring Boot 3.x + Java 21 (simulator, API, analytics)
├── agent/            # Python 3.11+ (RL training, Gymnasium env, SHAP, LSTM)
├── frontend/         # React 18 + TypeScript + Vite + Tailwind (dashboard)
├── infrastructure/   # Docker Compose, nginx config, .env templates
├── scripts/          # Cross-platform dev setup/run/build scripts
└── docs/             # Architecture, API reference, training guide
```

## Implementation Waves

### WAVE 0 — Project Scaffolding
- Create full directory tree
- `backend/pom.xml` — Spring Boot 3.2, dependencies: web, security, data-jpa, data-redis, kafka, websocket, validation, jjwt, Flyway
- `backend/src/.../CloudSentinelApplication.java` — minimal starter
- `backend/src/main/resources/application.yml` — dev profile, H2 for initial dev, MySQL for docker
- `agent/requirements.txt` — gymnasium, stable-baselines3, torch, numpy, requests, pyyaml, shap, scikit-learn
- `agent/pyproject.toml` + `__init__.py` files in all packages
- `frontend/` — Vite + React + TS + Tailwind scaffold
- `scripts/setup-dev.cmd` / `setup-dev.sh` — one-command dev setup
- `scripts/run-dev.cmd` / `run-dev.sh` — start all 3 services
- `.gitignore`, `.dockerignore`, `infrastructure/.env.example`

### WAVE 1 — Simulator Core + Gymnasium Wrapper + DQN Baseline (AI/RL Priority #1)

**Backend — Simulator:**
- `backend/src/.../simulator/model/` — `FailureMode.java` (enum), `ServiceNode.java`, `TopologyGraph.java`, `InfrastructureState.java`, `DifficultyLevel.java`
- `backend/src/.../simulator/engine/CausalStateMachine.java` — deterministic failure propagation, configurable delays, directed dependency graph
- `backend/src/.../simulator/engine/FailureInjector.java` — CPU spike, memory leak, service crash, network partition, database deadlock
- `backend/src/.../simulator/controller/SimulatorController.java` — `POST /api/v1/simulation/reset`, `POST /api/v1/simulation/step`, `GET /api/v1/simulation/state`
- Start with Level 1 (Simple) — single service, single failure mode

**Agent — Gymnasium Env + Q-Learning:**
- `agent/src/env/cloud_sentinel_env.py` — full `gymnasium.Env` with `reset()` and `step()` calling Spring Boot HTTP API
- `agent/src/env/http_client.py` — synchronous HTTP client to `http://localhost:8080`
- `agent/src/env/observation_space.py` — flattened normalized vector [0,1]: cpu, memory, error_rate, latency, tick_since_failure, cascade_depth
- `agent/src/env/action_space.py` — 9 discrete actions mapped to `Action` enum
- `agent/src/agents/dqn_agent.py` — Dueling DQN with basic replay buffer (PER added in Wave 2)
- `agent/src/training/trainer.py` — episode loop with logging, checkpointing every 50k steps
- `agent/notebooks/01_q_learning_baseline.ipynb` — interactive testing notebook
- `agent/scripts/train.py` — main training entry point

### WAVE 2 — Action Engine + Reward System + Full DQN+PER

**Backend — Action Engine:**
- `backend/src/.../action/model/ActionType.java` — 9 actions with costMultiplier, cooldownTicks, preconditions
- `backend/src/.../action/engine/PreconditionChecker.java` — validates action applicability per state
- `backend/src/.../action/engine/CooldownManager.java` — in-memory (dev) / Redis-backed (prod) cooldown tracking
- `backend/src/.../action/engine/MaskingEngine.java` — produces boolean validity mask per step
- `backend/src/.../action/engine/ActionExecutor.java` — applies actions to InfrastructureState

**Backend — Reward System:**
- `backend/src/.../reward/model/SLOTarget.java` — availability 99.9%, error_rate < 0.1%, p99 latency < 200ms, CPU < 80%
- `backend/src/.../reward/engine/RewardCalculator.java` — potential-based shaping: `reward = slo + gamma*phi(next) - phi(prev) - cost - unnecessary_penalty + proactive_bonus`
- `backend/src/.../reward/engine/PotentialBasedShaping.java` — phi(s) = weighted sum of (1-metric) for cpu, memory, error_rate, latency

**Agent — Full DQN+PER:**
- Upgrade `agent/src/agents/dqn_agent.py` to use Stable-Baselines3 `DQN` with `PrioritizedReplayBuffer`
- `agent/src/env/reward_adapter.py` — transforms backend reward response into scalar + metadata
- `agent/src/training/callback.py` — TensorBoard logging, checkpointing, eval SLO tracking
- Verify reward distribution over first 100 episodes before long training runs

### WAVE 3 — Difficulty Levels + Masked PPO + API Security

**Backend — Difficulty + Scenarios:**
- `backend/src/.../simulator/scenarios/SimpleScenario.java` — single failure, single service
- `backend/src/.../simulator/scenarios/MediumScenario.java` — multiple simultaneous failures
- `backend/src/.../simulator/scenarios/ComplexScenario.java` — 7-service topology, cascading failures, propagation delays
- `backend/src/.../simulator/scenarios/AdversarialScenario.java` — injects failure type agent handles worst

**Agent — Curriculum + PPO:**
- `agent/src/training/curriculum.py` — auto-promotes difficulty when success rate > 80%
- `agent/src/agents/masked_ppo_agent.py` — PPO with custom policy that applies action mask to logits
- `agent/scripts/curriculum_train.py` — progressive difficulty training script

**Backend — Security:**
- `backend/src/.../config/SecurityConfig.java` — Spring Security with JWT filter
- `backend/src/.../api/auth/JwtTokenProvider.java` + `JwtAuthenticationFilter.java`
- `backend/src/.../api/auth/AuthController.java` — login, register, refresh
- Role-based access: ADMIN (training), AGENT (Python process), USER (dashboard)

### WAVE 4 — Database Schema + Frontend Dashboard

**Backend — Database + Analytics:**
- `backend/src/main/resources/db/migration/V1__initial_schema.sql` — Flyway migration for episodes, transitions, agent_checkpoints, action_audit_log tables
- `backend/src/.../analytics/entity/` — JPA entities matching schema
- `backend/src/.../analytics/repository/` — Spring Data JPA repositories
- `backend/src/.../analytics/service/AnalyticsService.java` — KPI computation, benchmarking
- `POST /api/v1/simulation/inject-failure`, `GET /api/v1/simulation/topology`, `GET /api/v1/analytics/*`, `GET /api/v1/training/*`

**Frontend — Full Dashboard:**
- `frontend/src/services/api.ts` — Axios instance with JWT interceptor
- `frontend/src/stores/useStore.ts` — Zustand global state
- `frontend/src/hooks/useWebSocket.ts` — STOMP over SockJS for real-time state updates
- `frontend/src/components/topology/TopologyGraph.tsx` — D3 force-directed graph, health-colored nodes
- `frontend/src/components/charts/RewardChart.tsx` — Recharts live reward curve
- `frontend/src/components/charts/ActionDistribution.tsx` — action frequency heatmap
- `frontend/src/components/agent/AgentControl.tsx` — start/stop training, difficulty selector
- `frontend/src/pages/DashboardPage.tsx`, `TrainingPage.tsx`, `TopologyPage.tsx`
- `frontend/src/components/auth/LoginPage.tsx` + `ProtectedRoute.tsx`

### WAVE 5 — Advanced Features + Docker Compose

**Advanced:**
- `agent/src/models/shap_explainer.py` — SHAP DeepExplainer on trained model
- `backend/src/.../explainability/service/ShapService.java` — serves SHAP values via REST
- `frontend/src/components/explainability/FeatureImportance.tsx` — SHAP bar chart
- `agent/src/models/lstm_precursor.py` — LSTM failure precursor detector
- Human override: `POST /api/v1/simulation/override` + 30-tick freeze logic

**Docker Compose:**
- `infrastructure/docker-compose.yml` — spring-backend, python-agent, react-frontend, mysql, redis, kafka, zookeeper, nginx
- `backend/Dockerfile` — multi-stage Maven build, Java 21
- `agent/Dockerfile` — Python 3.11+, PyTorch CPU
- `frontend/Dockerfile` — multi-stage Node build + nginx
- `infrastructure/nginx/nginx.conf` — reverse proxy
- `scripts/build-docker.cmd` — one-command Docker build

## Verification Plan

### After Wave 1 (Critical Path):
1. Start Spring Boot backend: `cd backend && mvnw spring-boot:run`
2. Test API: `curl -X POST http://localhost:8080/api/v1/simulation/reset` → returns initial state
3. Test step: `curl -X POST http://localhost:8080/api/v1/simulation/step -d '{"action":0}'` → returns next state + reward
4. Start Python agent: `cd agent && python scripts/train.py --episodes 50`
5. Verify: Agent's mean reward increases over 50 episodes, loss decreases

### After Wave 2:
1. Verify action masking: inject a state where SCALE_DOWN is invalid → agent cannot select it
2. Verify reward shaping: run 100 episodes with logging → check reward distribution histogram
3. Compare DQN+PER vs basic DQN curves on TensorBoard

### After Wave 3:
1. Curriculum training: `python scripts/curriculum_train.py` → auto-promotes through difficulties
2. Masked PPO: verify PPO achieves higher SLO adherence than DQN on same topology
3. JWT security: unauthenticated requests to `/api/v1/training/*` return 401

### After Wave 4:
1. Frontend loads at `http://localhost:5173`, shows login page
2. After login, dashboard shows real-time metrics via WebSocket
3. Topology graph renders service nodes, colors change on failure injection
4. Analytics endpoints return correct KPIs from MySQL

### After Wave 5:
1. `docker compose -f infrastructure/docker-compose.yml up -d` → all services healthy
2. Full training run in Docker: agent connects to backend, trains, saves checkpoints
3. Frontend in Docker shows live training progress
4. SHAP endpoint returns feature importance for a given decision

## Key Technical Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Spring Boot build | Maven | Explicit dependency management, better IDE support |
| RL framework | Stable-Baselines3 | Production-grade, supports DQN + PPO |
| Action masking | Custom policy subclass | SB3 doesn't natively support masking |
| Config | YAML (Python), application.yml (Java) | Human-readable, versionable |
| Frontend state | Zustand | Minimal boilerplate vs Redux |
| WebSocket | STOMP over SockJS | Matches Spring's WebSocket support |
| Topology viz | D3.js (d3-force) | Flexible force-directed layout |
| Charts | Recharts | React-native, good TS types |
| DB migrations | Flyway | Versioned, repeatable |
| Initial dev DB | H2 in-memory | Zero infra for early dev, swap to MySQL for Docker |
| Kafka | Defer to Wave 5 | Use Spring ApplicationEventPublisher initially |

## Risk Mitigations

- **Python 3.14 compatibility**: PyTorch/SB3 may not support 3.14 yet. Fallback: install Python 3.11 alongside and use `py -3.11 -m venv`.
- **Windows Docker volumes**: Use WSL2 Docker backend. Avoid mounting node_modules/venv directories.
- **Reward design bugs**: Start simple, verify with 100-episode Q-Learning baseline before adding shaping.
- **Action masking in SB3**: Implement custom `BasePolicy` that applies mask to logits before softmax.
- **Memory pressure**: Set JVM heap to 512MB for dev. Use PyTorch CPU mode if no GPU.
- **CORS during dev**: Configure `WebConfig.java` for `localhost:5173`. Use Vite proxy as fallback.
