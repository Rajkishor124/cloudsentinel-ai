# CloudSentinel AI 🛡️

> AI-Driven Self-Healing Cloud Incident Response Platform

An autonomous SRE automation platform that monitors cloud infrastructure, detects failures in real-time, decides optimal remediation actions using a hybrid RL + rules engine, executes fixes, and continuously learns from outcomes.

## 🌟 Features

### Intelligent Failure Detection
- Real-time monitoring of CPU, memory, error rates, and latency
- Cascading failure detection across service dependency graphs
- Multi-severity alerting (INFO → CRITICAL) with recommended actions

### Hybrid Decision Engine
- **Reinforcement Learning**: DQN agent trained via potential-based reward shaping
- **Rule-Based Safety**: 7 emergency rules override RL for critical scenarios
- **Memory System**: Persistent healing experiences indexed by anomaly type

### Autonomous Self-Healing
- 9 remediation actions: Restart, Scale Up/Down, Clear Cache, Reroute Traffic, Circuit Breaker, etc.
- Before/after state tracking with effectiveness scoring
- Continuous learning from every healing cycle

### Real-Time Dashboard
- System health metrics with live charts (Recharts)
- D3 force-directed service topology visualization
- Alert management with severity filtering
- Training console with reward curve tracking

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     CloudSentinel AI Platform                    │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Frontend (React 18 + TypeScript + Vite)                 │   │
│  │  Dashboard · Self-Healing Console · Training · Topology   │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │ REST API / WebSocket              │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  Backend (Spring Boot 3.2 + Java 21)                     │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │   │
│  │  │  Monitor     │→│  Decide      │→│  Execute      │  │   │
│  │  │  Detector    │  │ Hybrid Engine│  │ Action Executor│  │   │
│  │  └──────────────┘  └──────────────┘  └───────────────┘  │   │
│  │         ↓                  ↓                  ↓           │   │
│  │  ┌──────────────┐  ┌──────────────┐  ┌───────────────┐  │   │
│  │  │  Learn       │←│  Memory      │←│  Feedback     │  │   │
│  │  │  Feedback    │  │ Healing Store│  │  Loop         │  │   │
│  │  └──────────────┘  └──────────────┘  └───────────────┘  │   │
│  └──────────────────────────────────────────────────────────┘   │
│                              │ Gymnasium Environment             │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  RL Agent (Python 3.11 + PyTorch + SB3)                  │   │
│  │  DQN Training · Evaluation · Self-Healing Loop           │   │
│  └──────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Python 3.11+
- Node.js 18+
- Maven 3.8+

### 1. Backend (Spring Boot)

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

Backend runs on **http://localhost:8080**

### 2. Frontend (React)

```powershell
cd frontend
npm install
npm run dev
```

Frontend runs on **http://localhost:3000**

### 3. RL Agent Training

```powershell
cd agent
pip install -e .
py -m scripts.train --difficulty SIMPLE --timesteps 500000
```

### 4. Self-Healing Mode

```powershell
py -m scripts.self_healing --cycles 100 --difficulty SIMPLE
```

### Docker Compose (Full Stack)

```powershell
cd infrastructure
docker-compose up -d
```

## 📁 Project Structure

```
cloudsentinel-ai/
├── backend/                 # Spring Boot backend
│   ├── src/main/java/com/cloudsentinel/
│   │   ├── detector/        # Failure detection & anomaly alerts
│   │   ├── decision/        # Hybrid decision engine (RL + rules)
│   │   ├── executor/        # Action execution with tracking
│   │   ├── feedback/        # Learning feedback loop
│   │   ├── memory/          # Persistent healing memory
│   │   ├── healing/         # Self-healing orchestrator
│   │   ├── simulator/       # Cloud simulation engine
│   │   └── reward/          # Multi-objective reward system
│   └── src/main/resources/  # Application configs
├── agent/                   # Python RL agent
│   ├── scripts/             # Training, evaluation, self-healing
│   ├── src/
│   │   ├── agents/          # DQN trainer
│   │   ├── env/             # Gymnasium environment
│   │   └── utils/           # Error classifier, fix engine
│   └── pyproject.toml       # Package configuration
├── frontend/                # React dashboard
│   └── src/
│       ├── components/      # Reusable UI components
│       ├── pages/           # Dashboard, Healing, Training, Topology
│       ├── stores/          # Zustand state management
│       ├── lib/             # API client
│       └── types/           # TypeScript type definitions
└── infrastructure/          # Docker Compose deployment
```

## 🧠 AI/ML Details

### Observation Space
- **Size**: 31 features per frame (7 services × 4 metrics + 3 trend deltas)
- **Stacked**: 4 frames → 124 total input dimensions
- **Metrics**: CPU, Memory, Error Rate, Latency per service + derived trends

### Action Space (9 Actions)
| Action | Cost | Use Case |
|--------|------|----------|
| DO_NOTHING | 0.0 | System healthy |
| RESTART_SERVICE | 0.05 | CPU spike, memory leak |
| SCALE_UP | 0.10 | High load, latency |
| SCALE_DOWN | 0.0 | Underutilized resources |
| CLEAR_CACHE | 0.01 | Memory pressure |
| RESTART_DATABASE | 0.15 | Database deadlock |
| REROUTE_TRAFFIC | 0.08 | High error rate |
| ROLLBACK_DEPLOYMENT | 0.12 | Bad deployment |
| TRIGGER_CIRCUIT_BREAKER | 0.02 | Cascading failures |

### Reward Function
Multi-objective reward shaping:
```
Reward = SLO_score + γ(φ_next - φ_prev) - cost_penalty 
         - unnecessary_penalty + proactive_bonus 
         + stability_reward - failure_penalty + recovery_bonus
```

## 📊 API Endpoints

### Simulation
- `POST /api/v1/simulation/reset` - Reset environment
- `POST /api/v1/simulation/step` - Execute action
- `GET /api/v1/simulation/state` - Get current state
- `GET /api/v1/simulation/topology` - Service dependency graph

### Self-Healing
- `POST /api/v1/healing/cycle` - Execute healing cycle
- `GET /api/v1/healing/status` - Orchestrator status
- `GET /api/v1/healing/alerts` - Active anomaly alerts
- `GET /api/v1/healing/memory/stats` - Healing memory statistics
- `GET /api/v1/healing/feedback/metrics` - Learning metrics

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3.2, Java 21, H2/MySQL, Redis, Kafka |
| RL Agent | Python 3.11, PyTorch, Stable-Baselines3, Gymnasium |
| Frontend | React 18, TypeScript, Vite, Tailwind CSS |
| Visualization | D3.js, Recharts |
| State Management | Zustand |
| Infrastructure | Docker Compose, Nginx |

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- OpenAI Gymnasium for the RL environment standard
- Stable-Baselines3 for the DQN implementation
- D3.js for graph visualization capabilities

---

Built with ❤️ by Rajkishor Murmu
