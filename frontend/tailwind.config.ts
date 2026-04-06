import type { Config } from 'tailwindcss';



const config: Config = {

    darkMode: 'class',

    content: ['./index.html', './src/**/*.{ts,tsx}'],

    theme: {

        extend: {

            colors: {

                // Surface hierarchy (darkest → brightest)

                'surface-lowest': '#0a0e14',

                'surface-low': '#181c22',

                'surface': '#10141a',

                'surface-container': '#1c2026',

                'surface-high': '#262a31',

                'surface-highest': '#31353c',

                'surface-bright': '#353940',



                // Brand

                primary: '#3adfab',

                'primary-container': '#00a47b',

                'primary-fixed': '#60fcc6',

                'on-primary': '#003828',

                'on-primary-container': '#003122',



                // Secondary (blue)

                secondary: '#adc6ff',

                'secondary-container': '#0566d9',

                'on-secondary': '#002e6a',



                // Tertiary (amber)

                tertiary: '#ffb95f',

                'tertiary-container': '#ca8100',

                'on-tertiary': '#472a00',



                // Error

                error: '#ffb4ab',

                'error-container': '#93000a',

                'on-error': '#690005',

                'on-error-container': '#ffdad6',



                // Text

                'on-surface': '#dfe2eb',

                'on-surface-variant': '#c2c6d6',



                // Borders

                'outline': '#8c909f',

                'outline-variant': '#424754',



                background: '#10141a',

                'on-background': '#dfe2eb',



                // Semantic aliases for existing code compatibility

                'sentinel-text': '#dfe2eb',

                'sentinel-muted': '#c2c6d6',

                'sentinel-card': '#1c2026',

                'sentinel-dark': '#181c22',

                'sentinel-border': '#424754',

                'sentinel-blue': '#adc6ff',

                'sentinel-green': '#3adfab',

                'sentinel-red': '#ffb4ab',

                'sentinel-yellow': '#ffb95f',

            },

            fontFamily: {

                headline: ['Space Grotesk', 'sans-serif'],

                body: ['Inter', 'sans-serif'],

                mono: ['Space Mono', 'monospace'],

            },

            borderRadius: {

                DEFAULT: '0.125rem',

                sm: '0.25rem',

                md: '0.375rem',

                lg: '0.5rem',

                xl: '0.75rem',

                '2xl': '1rem',

                full: '9999px',

            },

            keyframes: {

                ripple: {

                    '0%': { transform: 'scale(1)', opacity: '0.5' },

                    '100%': { transform: 'scale(2.5)', opacity: '0' },

                },

                'spin-slow': {

                    from: { transform: 'rotate(0deg)' },

                    to: { transform: 'rotate(360deg)' },

                },

                'fade-in': {

                    from: { opacity: '0', transform: 'translateY(4px)' },

                    to: { opacity: '1', transform: 'translateY(0)' },

                },

                blink: {

                    '0%, 100%': { opacity: '1' },

                    '50%': { opacity: '0' },

                },

            },

            animation: {

                ripple: 'ripple 2s infinite ease-out',

                'spin-slow': 'spin-slow 8s linear infinite',

                'fade-in': 'fade-in 0.3s ease forwards',

                blink: 'blink 1s step-end infinite',

            },

            boxShadow: {

                'glow-primary': '0 0 15px rgba(58,223,171,0.15)',

                'glow-error': '0 0 10px rgba(255,180,171,0.15)',

                sidebar: '32px 0 32px rgba(0,67,149,0.06)',

            },

        },

    },

    plugins: [],

};



export default config;
