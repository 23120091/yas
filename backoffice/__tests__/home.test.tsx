jest.mock('next/router');
import '@testing-library/jest-dom';
import { render, screen } from '@testing-library/react'
import Home from '../pages/index'

test('renders homepage', () => {
  render(<Home />);
  expect(
    screen.getByText(/list of the 5 latest products/i)
  ).toBeInTheDocument();
})