import { NextPage } from 'next';
import Head from 'next/head';
import Link from 'next/link';

interface ErrorPageProps {
  statusCode?: number;
}

const ErrorPage: NextPage<ErrorPageProps> = ({ statusCode }) => {
  return (
    <>
      <Head>
        <title>Error - YAS Backoffice</title>
      </Head>
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '60vh',
          padding: '2rem',
          textAlign: 'center',
        }}
      >
        <h2>{statusCode ? `Error ${statusCode}` : 'An error occurred'}</h2>
        <p>Something went wrong. Please try again later.</p>
        <Link href="/" style={{ marginTop: '1rem' }}>
          Go to Dashboard
        </Link>
      </div>
    </>
  );
};

ErrorPage.getInitialProps = ({ res, err }) => {
  const statusCode = res ? res.statusCode : err ? err.statusCode : 404;
  return { statusCode };
};

export default ErrorPage;
