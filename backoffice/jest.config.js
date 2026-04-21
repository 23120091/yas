const nextJest = require('next/jest')

const createJestConfig = nextJest({
  dir: './',
})

const customJestConfig = {
  testEnvironment: 'jsdom',
  moduleNameMapper: {
    '^modules/(.*)$': '<rootDir>/modules/$1',
  },
}

module.exports = createJestConfig(customJestConfig)