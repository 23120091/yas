const nextJest = require('next/jest')

const createJestConfig = nextJest({
  dir: './',
})

const customJestConfig = {
  setupFilesAfterEnv: ['<rootDir>/__tests__/setupTests.ts'],
  testEnvironment: 'jsdom',
  moduleNameMapper: {
    '^modules/(.*)$': '<rootDir>/modules/$1',
    '^@catalogModels/(.*)$': '<rootDir>/modules/catalog/models/$1',
    '^@/(.*)$': '<rootDir>/$1',
    '^@commonServices/(.*)$': '<rootDir>/common/services/$1',
    '^@catalogServices/(.*)$': '<rootDir>/modules/catalog/services/$1',
  },
  testPathIgnorePatterns: ['<rootDir>/__tests__/setupTests.ts'],
}

module.exports = createJestConfig(customJestConfig)