import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The dev-server proxy makes CORS a non-issue during development:
// the browser calls /api/* here, Vite forwards it to Spring Boot on :18080.
// (Port moved off the crowded :8080 so other local projects never collide.)
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:18080',
      '/ws': { target: 'http://localhost:18080', ws: true },
    },
  },
})
