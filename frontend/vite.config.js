import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev-server proxy makes CORS a non-issue during development:
// the browser calls /api/* here, Vite forwards it to Spring Boot on :8080.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws': { target: 'http://localhost:8080', ws: true },
    },
  },
})
