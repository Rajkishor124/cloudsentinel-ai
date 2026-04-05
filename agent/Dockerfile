FROM python:3.11-slim
WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create directories for checkpoints and logs
RUN mkdir -p checkpoints logs

ENV SIMULATOR_API_URL=http://spring-backend:8080
ENV CUDA_VISIBLE_DEVICES=""

CMD ["python", "scripts/train.py"]
