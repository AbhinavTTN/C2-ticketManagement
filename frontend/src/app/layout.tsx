import type { Metadata } from 'next';
import './globals.css';
import { AiChatProvider } from '@/components/AiChatContext';
import Navbar from '@/components/Navbar';
import AiChatPanel from '@/components/AiChatPanel';

export const metadata: Metadata = {
  title: 'Ticket Management & RAG Q&A Desk',
  description: 'Enterprise support ticket management system with grounded AI question answering',
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-slate-50 text-slate-900 antialiased">
        <AiChatProvider>
          <div className="flex min-h-screen flex-col">
            <Navbar />
            <main className="flex-1 pb-16">{children}</main>
            <AiChatPanel />
          </div>
        </AiChatProvider>
      </body>
    </html>
  );
}
