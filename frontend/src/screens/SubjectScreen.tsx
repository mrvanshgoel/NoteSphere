import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { supabase } from '../lib/supabase';
import { useAuth } from '../contexts/AuthContext';
import { ArrowLeft, UploadCloud, FileText, Loader2, Image as ImageIcon } from 'lucide-react';
import { motion } from 'framer-motion';

interface Material {
  id: string;
  name: string;
  type: string;
  file_url: string;
  created_at: string;
}

const SubjectScreen = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const [subject, setSubject] = useState<any>(null);
  const [materials, setMaterials] = useState<Material[]>([]);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState(0);

  useEffect(() => {
    fetchSubjectDetails();
    fetchMaterials();
  }, [id]);

  const fetchSubjectDetails = async () => {
    const { data } = await supabase.from('subjects').select('*').eq('id', id).single();
    if (data) setSubject(data);
  };

  const fetchMaterials = async () => {
    const { data } = await supabase
      .from('materials')
      .select('*')
      .eq('subject_id', id)
      .order('created_at', { ascending: false });
    if (data) setMaterials(data);
  };

  const handleFileUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !user) return;

    setIsUploading(true);
    setUploadProgress(10); // Fake progress to show UI update

    try {
      const fileExt = file.name.split('.').pop();
      const fileName = `${Math.random()}.${fileExt}`;
      const filePath = `${user.id}/${id}/${fileName}`;

      const { error: uploadError } = await supabase.storage
        .from('study_materials')
        .upload(filePath, file);

      if (uploadError) throw uploadError;
      setUploadProgress(70);

      const { data: urlData } = supabase.storage
        .from('study_materials')
        .getPublicUrl(filePath);

      const { error: dbError } = await supabase.from('materials').insert([{
        subject_id: id,
        user_id: user.id,
        name: file.name,
        type: file.type,
        file_url: urlData.publicUrl,
        storage_path: filePath
      }]);

      if (dbError) throw dbError;

      setUploadProgress(100);
      fetchMaterials();
    } catch (err) {
      console.error('Upload failed:', err);
      alert('Upload failed. Check console for details.');
    } finally {
      setTimeout(() => {
        setIsUploading(false);
        setUploadProgress(0);
      }, 500);
    }
  };

  return (
    <div className="min-h-screen bg-background pb-20">
      {/* Header */}
      <div className="glass-panel sticky top-0 z-40 px-4 pt-12 pb-4 flex items-center gap-3 border-b border-white/5">
        <button 
          onClick={() => navigate('/')}
          className="w-10 h-10 rounded-full bg-white/5 flex items-center justify-center hover:bg-white/10 transition-colors"
        >
          <ArrowLeft className="w-5 h-5" />
        </button>
        <h1 className="text-xl font-bold truncate">{subject?.name || 'Loading...'}</h1>
      </div>

      <div className="p-4">
        {/* Upload Button */}
        <div className="mb-6 relative">
          <input 
            type="file" 
            id="file-upload" 
            className="hidden" 
            onChange={handleFileUpload}
            disabled={isUploading}
            accept=".pdf,.txt,.png,.jpg,.jpeg"
          />
          <label 
            htmlFor="file-upload"
            className={`w-full py-8 border-2 border-dashed rounded-2xl flex flex-col items-center justify-center gap-2 transition-all
              ${isUploading ? 'border-primary/50 bg-primary/5' : 'border-white/10 hover:border-primary/30 hover:bg-white/[0.02] cursor-pointer'}`}
          >
            {isUploading ? (
              <>
                <Loader2 className="w-8 h-8 text-primary animate-spin" />
                <p className="text-sm font-medium text-primary">Uploading... {uploadProgress}%</p>
              </>
            ) : (
              <>
                <div className="w-12 h-12 bg-primary/20 text-primary rounded-full flex items-center justify-center mb-2">
                  <UploadCloud className="w-6 h-6" />
                </div>
                <p className="font-semibold">Tap to upload material</p>
                <p className="text-xs text-muted-foreground">PDF, Notes, Images</p>
              </>
            )}
          </label>
        </div>

        {/* Files List */}
        <div>
          <h2 className="text-sm font-semibold text-muted-foreground uppercase tracking-wider mb-3 px-1">
            Study Materials ({materials.length})
          </h2>
          <div className="space-y-3">
            {materials.map((mat) => (
              <motion.div
                key={mat.id}
                whileHover={{ scale: 0.99 }}
                onClick={() => navigate(`/doc/${mat.id}`)}
                className="glass-card p-4 rounded-2xl flex items-center gap-4 cursor-pointer hover:bg-white/[0.05]"
              >
                <div className="w-12 h-12 rounded-xl bg-gradient-to-br from-blue-500/20 to-purple-500/20 flex items-center justify-center">
                  {mat.type.includes('image') ? (
                    <ImageIcon className="w-6 h-6 text-purple-400" />
                  ) : (
                    <FileText className="w-6 h-6 text-blue-400" />
                  )}
                </div>
                <div className="flex-1 min-w-0">
                  <h3 className="font-medium text-white truncate">{mat.name}</h3>
                  <p className="text-xs text-muted-foreground">
                    {new Date(mat.created_at).toLocaleDateString()}
                  </p>
                </div>
              </motion.div>
            ))}
            {materials.length === 0 && !isUploading && (
              <div className="text-center py-10 px-4 text-muted-foreground border border-white/5 rounded-xl">
                No materials yet. Upload your first file to start studying with AI!
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default SubjectScreen;
