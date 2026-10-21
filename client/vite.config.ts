import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// API calls go straight to VITE_API_BASE_URL (see .env); the backend allows these origins via CORS.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, strictPort: true },
  preview: { port: 4173, strictPort: true },
})
