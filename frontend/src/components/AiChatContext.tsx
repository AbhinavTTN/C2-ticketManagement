'use client';

import React, { createContext, useContext, useState } from 'react';

interface AiChatContextType {
  isOpen: boolean;
  openPanel: (initialQuestion?: string) => void;
  closePanel: () => void;
  togglePanel: () => void;
}

const AiChatContext = createContext<AiChatContextType | undefined>(undefined);

export function AiChatProvider({ children }: { children: React.ReactNode }) {
  const [isOpen, setIsOpen] = useState(false);

  const openPanel = () => setIsOpen(true);
  const closePanel = () => setIsOpen(false);
  const togglePanel = () => setIsOpen((prev) => !prev);

  return (
    <AiChatContext.Provider value={{ isOpen, openPanel, closePanel, togglePanel }}>
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
