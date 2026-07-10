import { defineStore } from 'pinia';
import { ref } from 'vue';

export const useThemeStore = defineStore('theme', () => {
  const theme = ref(localStorage.getItem('theme') || 'dark');

  const applyTheme = (newTheme) => {
    theme.value = newTheme;
    localStorage.setItem('theme', newTheme);
    
    // 动态添加/移除 html 上的 class
    document.documentElement.className = newTheme;
  };

  const toggleTheme = () => {
    const nextTheme = theme.value === 'dark' ? 'light' : 'dark';
    applyTheme(nextTheme);
  };

  // 初始化时同步应用
  applyTheme(theme.value);

  return {
    theme,
    toggleTheme
  };
});
