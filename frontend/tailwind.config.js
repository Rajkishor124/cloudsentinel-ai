/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'sentinel-dark': '#0a0e1a',
        'sentinel-card': '#111827',
        'sentinel-border': '#1f2937',
        'sentinel-text': '#e5e7eb',
        'sentinel-muted': '#9ca3af',
        'sentinel-green': '#10b981',
        'sentinel-red': '#ef4444',
        'sentinel-yellow': '#f59e0b',
        'sentinel-blue': '#3b82f6',
      },
    },
  },
  plugins: [],
};
