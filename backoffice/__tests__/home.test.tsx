jest.mock('next/router', () => require('../__mocks__/next/router'));
import { render, screen } from '@testing-library/react'
import Home from '../pages/index'

test('renders homepage', () => {
  render(<Home />)
  expect(screen.getByText(/welcome/i)).toBeInTheDocument()
})