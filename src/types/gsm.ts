export type FrameworkType = 'qt' | 'flutter' | 'imgui' | 'tauri';

export type DeviceChipset = 'mediatek' | 'qualcomm' | 'unisoc' | 'samsung' | 'fastboot';

export type ToolSkin = 'qt-neon' | 'flutter-modern' | 'imgui-hacker';

export interface PartitionItem {
  name: string;
  filename: string;
  sizeMB: number;
  offsetHex: string;
  selected: boolean;
  status: 'idle' | 'reading' | 'writing' | 'verified' | 'error';
  progress: number;
}

export interface FlashingLog {
  id: string;
  timestamp: string;
  level: 'info' | 'warn' | 'error' | 'success' | 'debug' | 'data';
  message: string;
  hexDump?: string;
}

export interface ArchitectureMetric {
  id: string;
  titleEn: string;
  titleMm: string;
  category: 'performance' | 'crossplatform' | 'usb' | 'commercial' | 'devspeed';
  descriptionEn: string;
  descriptionMm: string;
  scores: {
    qt: number; // 1 - 10
    flutter: number;
    imgui: number;
    tauri: number;
  };
  details: {
    qt: string;
    flutter: string;
    imgui: string;
    tauri: string;
  };
}

export interface CodeTemplate {
  id: string;
  title: string;
  framework: FrameworkType;
  language: string;
  category: 'usb-engine' | 'android-otg' | 'partition-table' | 'logger' | 'security';
  descriptionMm: string;
  descriptionEn: string;
  fileName: string;
  code: string;
}

export interface ProjectAnalysisResult {
  fileName: string;
  detectedFramework: string;
  identifiedComponents: string[];
  usbHandlingAssessment: string;
  androidCompatibility: string;
  performanceScore: number;
  recommendationsMm: string[];
  recommendationsEn: string[];
}
