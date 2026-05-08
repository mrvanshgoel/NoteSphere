import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { supabase } from '../lib/supabase';
import { useAuth } from '../contexts/AuthContext';
import { ArrowLeft, MessageSquare, BookOpen, PenTool, BrainCircuit, Loader2, Send } from 'lucide-react';
import { motion, AnimatePresence } from 'framer-motion';

const DocumentScreen = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { } = useAuth();
  
  const [material, setMaterial] = useState<any>(null);
  const [activeTab, setActiveTab] = useState<'ai' | 'chat'>('ai');
  const [isProcessing, setIsProcessing] = useState(false);
  const [aiResult, setAiResult] = useState<string | null>(null);
  
  // Chat state
  const [chatInput, setChatInput] = useState('');
  const [chatHistory, setChatHistory] = useState<{role: string, content: string}[]>([]);
  const [isChatting, setIsChatting] = useState(false);

  useEffect(() => {
    fetchMaterialDetails();
  }, [id]);

  const fetchMaterialDetails = async () => {
    const { data } = await supabase.from('materials').select('*').eq('id', id).single();
    if (data) setMaterial(data);
  };

  const handleAIAction = async (type: 'summary' | 'notes' | 'questions') => {
    setIsProcessing(true);
    setAiResult(null);
    try {
      // In a real app, you would extract text from the file_url or send the file directly to backend
      // Here we simulate document text to demonstrate the integration
      const documentText = "This is a placeholder for the actual extracted document text. Imagine it contains the contents of the uploaded file.";
      
      const response = await fetch('http://localhost:5000/api/ai/process-document', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`
        },
        body: JSON.stringify({ documentText, type })
      });
      
      const data = await response.json();
      if (response.ok) {
        setAiResult(data.result);
      } else {
        alert(data.error || 'Failed to process document');
      }
    } catch (err) {
      console.error(err);
      alert('Network error communicating with AI server.');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleSendMessage = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!chatInput.trim() || isChatting) return;

    const userMessage = chatInput.trim();
    setChatInput('');
    setChatHistory(prev => [...prev, { role: 'user', content: userMessage }]);
    setIsChatting(true);

    try {
      const documentText = "This is a placeholder for the actual extracted document text.";
      
      const response = await fetch('http://localhost:5000/api/ai/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${(await supabase.auth.getSession()).data.session?.access_token}`
        },
        body: JSON.stringify({ 
          documentText, 
          question: userMessage,
          history: chatHistory 
        })
      });
      
      const data = await response.json();
      if (response.ok) {
        setChatHistory(prev => [...prev, { role: 'assistant', content: data.result }]);
      } else {
        alert(data.error || 'Failed to get answer');
      }
    } catch (err) {
      console.error(err);
      alert('Network error.');
    } finally {
      setIsChatting(false);
    }
  };

  return (
    <div className="min-h-screen bg-background flex flex-col">
      {/* Header */}
      <div className="glass-panel sticky top-0 z-40 px-4 pt-12 pb-4 border-b border-white/5">
        <div className="flex items-center gap-3 mb-4">
          <button 
            onClick={() => navigate(-1)}
            className="w-10 h-10 rounded-full bg-white/5 flex items-center justify-center hover:bg-white/10"
          >
            <ArrowLeft className="w-5 h-5" />
          </button>
          <div className="flex-1 min-w-0">
            <h1 className="text-base font-bold truncate">{material?.name || 'Loading...'}</h1>
            <p className="text-xs text-muted-foreground uppercase tracking-wider">Document Options</p>
          </div>
        </div>

        {/* Custom Tabs */}
        <div className="flex p-1 bg-black/20 rounded-xl">
          <button
            onClick={() => setActiveTab('ai')}
            className={`flex-1 py-2 text-sm font-medium rounded-lg transition-all flex items-center justify-center gap-2 ${activeTab === 'ai' ? 'bg-primary text-white shadow-md' : 'text-muted-foreground hover:text-white'}`}
          >
            <BrainCircuit className="w-4 h-4" /> AI Actions
          </button>
          <button
            onClick={() => setActiveTab('chat')}
            className={`flex-1 py-2 text-sm font-medium rounded-lg transition-all flex items-center justify-center gap-2 ${activeTab === 'chat' ? 'bg-primary text-white shadow-md' : 'text-muted-foreground hover:text-white'}`}
          >
            <MessageSquare className="w-4 h-4" /> Q&A Chat
          </button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-4 pb-24">
        {activeTab === 'ai' ? (
          <div className="space-y-6">
            <div className="grid grid-cols-3 gap-3">
              <button 
                onClick={() => handleAIAction('summary')}
                className="flex flex-col items-center justify-center gap-2 p-4 glass-card rounded-2xl hover:bg-primary/20 hover:border-primary/50 transition-all group"
              >
                <BookOpen className="w-6 h-6 text-blue-400 group-hover:scale-110 transition-transform" />
                <span className="text-xs font-medium">Summary</span>
              </button>
              <button 
                onClick={() => handleAIAction('notes')}
                className="flex flex-col items-center justify-center gap-2 p-4 glass-card rounded-2xl hover:bg-green-500/20 hover:border-green-500/50 transition-all group"
              >
                <PenTool className="w-6 h-6 text-green-400 group-hover:scale-110 transition-transform" />
                <span className="text-xs font-medium">Notes</span>
              </button>
              <button 
                onClick={() => handleAIAction('questions')}
                className="flex flex-col items-center justify-center gap-2 p-4 glass-card rounded-2xl hover:bg-purple-500/20 hover:border-purple-500/50 transition-all group"
              >
                <BrainCircuit className="w-6 h-6 text-purple-400 group-hover:scale-110 transition-transform" />
                <span className="text-xs font-medium">Practice</span>
              </button>
            </div>

            <AnimatePresence mode="wait">
              {isProcessing && (
                <motion.div 
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -10 }}
                  className="flex flex-col items-center justify-center py-12"
                >
                  <Loader2 className="w-8 h-8 text-primary animate-spin mb-4" />
                  <p className="text-primary font-medium animate-pulse">AI is reading your document...</p>
                </motion.div>
              )}

              {aiResult && !isProcessing && (
                <motion.div
                  initial={{ opacity: 0, y: 10 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="glass-card p-6 rounded-2xl border border-primary/30"
                >
                  <div className="prose prose-invert max-w-none text-sm leading-relaxed whitespace-pre-wrap">
                    {aiResult}
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        ) : (
          <div className="space-y-4">
            {chatHistory.length === 0 && (
              <div className="text-center py-12 text-muted-foreground flex flex-col items-center">
                <div className="w-16 h-16 bg-primary/10 rounded-full flex items-center justify-center mb-4">
                  <MessageSquare className="w-8 h-8 text-primary" />
                </div>
                <p>Ask anything about this document!</p>
              </div>
            )}
            
            {chatHistory.map((msg, idx) => (
              <div key={idx} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                <div className={`max-w-[85%] p-4 rounded-2xl ${
                  msg.role === 'user' 
                    ? 'bg-primary text-white rounded-br-none' 
                    : 'glass-card rounded-bl-none text-gray-200'
                }`}>
                  <p className="text-sm whitespace-pre-wrap">{msg.content}</p>
                </div>
              </div>
            ))}
            
            {isChatting && (
              <div className="flex justify-start">
                <div className="glass-card p-4 rounded-2xl rounded-bl-none flex items-center gap-2">
                  <Loader2 className="w-4 h-4 text-primary animate-spin" />
                  <span className="text-sm text-muted-foreground">Thinking...</span>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* Chat Input Bar */}
      {activeTab === 'chat' && (
        <div className="fixed bottom-0 left-0 right-0 p-4 bg-background/80 backdrop-blur-xl border-t border-white/5">
          <form onSubmit={handleSendMessage} className="flex gap-2">
            <input
              type="text"
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              placeholder="Ask a question..."
              className="flex-1 bg-black/30 border border-white/10 rounded-full px-5 py-3 text-sm focus:outline-none focus:border-primary/50 text-white"
            />
            <button 
              type="submit"
              disabled={!chatInput.trim() || isChatting}
              className="w-12 h-12 rounded-full bg-primary flex items-center justify-center text-white disabled:opacity-50 disabled:bg-primary/50"
            >
              <Send className="w-5 h-5" />
            </button>
          </form>
        </div>
      )}
    </div>
  );
};

export default DocumentScreen;
