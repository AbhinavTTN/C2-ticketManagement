'use client';

import React, { createContext, useContext, useState } from 'react';

interface AiChatContextType {
  isOpen: boolean;
  revision: number;
  openPanel: (initialQuestion?: string) => void;
  closePanel: () => void;
  togglePanel: () => void;
  notifyTicketsChanged: () => void;
}

const AiChatContext = createContext<AiChatContextType | undefined>(undefined);

export function AiChatProvider({ children }: { children: React.ReactNode }) {
  const [isOpen, setIsOpen] = useState(false);
  const [revision, setRevision] = useState(0);

  const openPanel = () => setIsOpen(true);
  const closePanel = () => setIsOpen(false);
  const togglePanel = () => setIsOpen((prev) => !prev);
  const notifyTicketsChanged = () => setRevision((value) => value + 1);

  return (
    <AiChatContext.Provider
      value={{ isOpen, revision, openPanel, closePanel, togglePanel, notifyTicketsChanged }}
    >
      {children}
    </AiChatContext.Provider>
  );
}

export function useAiChat() {
  const context = useContext(AiChatContext);
  if (!context) {
    throw new Error('useAiChat must be used within an AiChatProvider');
  }
  return context;
}
