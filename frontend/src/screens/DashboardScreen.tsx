import React, { useEffect, useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { supabase } from '../lib/supabase';
import { LogOut, Folder, Plus, FileText, Sparkles, BookOpen } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { motion } from 'framer-motion';

interface Subject {
  id: string;
  name: string;
  color: string;
}

const COLORS = ['bg-blue-500', 'bg-purple-500', 'bg-green-500', 'bg-red-500', 'bg-yellow-500', 'bg-pink-500'];

const DashboardScreen = () => {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [isAddingSubject, setIsAddingSubject] = useState(false);
  const [newSubjectName, setNewSubjectName] = useState('');

  useEffect(() => {
    fetchSubjects();
  }, [user]);

  const fetchSubjects = async () => {
    if (!user) return;
    const { data, error } = await supabase
      .from('subjects')
      .select('*')
      .eq('user_id', user.id)
      .order('created_at', { ascending: false });

    if (!error && data) {
      setSubjects(data);
    }
  };

  const handleAddSubject = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newSubjectName.trim() || !user) return;

    const randomColor = COLORS[Math.floor(Math.random() * COLORS.length)];
    
    const { data, error } = await supabase
      .from('subjects')
      .insert([{ name: newSubjectName, user_id: user.id, color: randomColor }])
      .select();

    if (!error && data) {
      setSubjects([data[0], ...subjects]);
      setNewSubjectName('');
      setIsAddingSubject(false);
    }
  };

  return (
    <div className="min-h-screen bg-background pb-20">
      {/* Header */}
      <div className="glass-panel sticky top-0 z-40 px-4 pt-12 pb-4 flex justify-between items-center rounded-b-3xl">
        <div>
          <p className="text-muted-foreground text-sm">Welcome back,</p>
          <h1 className="text-xl font-bold text-white truncate w-48">{user?.email}</h1>
        </div>
        <button 
          onClick={signOut}
          className="w-10 h-10 rounded-full bg-white/10 flex items-center justify-center hover:bg-red-500/20 hover:text-red-400 transition-colors"
        >
          <LogOut className="w-5 h-5" />
        </button>
      </div>

      <div className="p-4 space-y-8">
        {/* AI Insight Card */}
        <motion.div 
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="rounded-2xl bg-gradient-to-br from-primary/20 to-purple-600/20 border border-white/10 p-5 relative overflow-hidden"
        >
          <div className="absolute top-0 right-0 p-4 opacity-20">
            <Sparkles className="w-24 h-24" />
          </div>
          <div className="relative z-10">
            <div className="flex items-center gap-2 text-primary mb-2">
              <Sparkles className="w-5 h-5" />
              <h2 className="font-semibold">AI Study Plan</h2>
            </div>
            <p className="text-sm text-gray-300 leading-relaxed">
              Based on your recent uploads, you should review your Math formulas and read Chapter 4 of Physics. Would you like to generate a practice quiz?
            </p>
            <button className="mt-4 text-xs font-medium bg-primary/20 hover:bg-primary/30 text-primary-foreground py-2 px-4 rounded-full transition-colors backdrop-blur-sm border border-primary/30">
              Generate Quiz
            </button>
          </div>
        </motion.div>

        {/* Subjects Section */}
        <div>
          <div className="flex justify-between items-center mb-4">
            <h2 className="text-lg font-semibold flex items-center gap-2">
              <BookOpen className="w-5 h-5 text-purple-400" />
              My Subjects
            </h2>
            <button 
              onClick={() => setIsAddingSubject(true)}
              className="text-primary text-sm font-medium flex items-center gap-1"
            >
              <Plus className="w-4 h-4" /> Add
            </button>
          </div>

          {isAddingSubject && (
            <motion.form 
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              onSubmit={handleAddSubject} 
              className="mb-4 glass-card p-3 rounded-xl flex gap-2"
            >
              <input
                autoFocus
                type="text"
                value={newSubjectName}
                onChange={(e) => setNewSubjectName(e.target.value)}
                placeholder="Subject Name (e.g. History)"
                className="flex-1 bg-black/20 border border-white/10 rounded-lg px-3 py-2 text-sm text-white focus:outline-none focus:border-primary/50"
              />
              <button 
                type="submit"
                className="bg-primary text-white px-4 py-2 rounded-lg text-sm font-medium"
              >
                Save
              </button>
            </motion.form>
          )}

          <div className="grid grid-cols-2 gap-3">
            {subjects.length === 0 && !isAddingSubject && (
              <div className="col-span-2 text-center py-8 text-muted-foreground border border-dashed border-white/10 rounded-xl">
                No subjects yet. Create one to start storing materials!
              </div>
            )}
            {subjects.map((subject) => (
              <motion.div
                key={subject.id}
                whileHover={{ scale: 0.98 }}
                onClick={() => navigate(`/subject/${subject.id}`)}
                className="glass-card p-4 rounded-2xl cursor-pointer hover:bg-white/[0.03] transition-colors border-l-4 border-l-transparent"
                style={{ borderLeftColor: subject.color.replace('bg-', '') }} // simplified color application
              >
                <div className={`w-10 h-10 ${subject.color} rounded-xl flex items-center justify-center mb-3 shadow-lg`}>
                  <Folder className="w-5 h-5 text-white" />
                </div>
                <h3 className="font-semibold text-white">{subject.name}</h3>
                <p className="text-xs text-muted-foreground mt-1">0 items</p>
              </motion.div>
            ))}
          </div>
        </div>

        {/* Recent Materials */}
        <div>
          <h2 className="text-lg font-semibold flex items-center gap-2 mb-4">
            <FileText className="w-5 h-5 text-blue-400" />
            Recent Materials
          </h2>
          <div className="space-y-3">
            <div className="text-center py-8 text-muted-foreground border border-dashed border-white/10 rounded-xl text-sm">
              Recent materials will appear here.
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardScreen;
