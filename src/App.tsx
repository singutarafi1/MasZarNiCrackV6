import React, { useState, useEffect, useRef, useMemo } from 'react';
import {
  Cpu,
  ShieldCheck,
  Usb,
  Terminal,
  CheckCircle2,
  AlertTriangle,
  Play,
  RotateCcw,
  Layers,
  Code2,
  Palette,
  HardDrive,
  Activity,
  Zap,
  Lock,
  Boxes,
  FileCode,
  Copy,
  Check,
  Sliders,
  Database,
  Smartphone,
  ChevronDown,
  FolderOpen,
  FileUp,
  Square,
  CheckSquare,
  Maximize2,
  Minimize2,
  Sparkles,
  Info,
  Key,
  Cloud,
  CloudDownload,
  RefreshCw,
  Search,
  X
} from 'lucide-react';
import { BURMESE_GSM_DISCUSSIONS } from './data/burmeseDiscussion.ts';
import { ToolSkin, PartitionItem } from './types/gsm.ts';

interface LoaderCatalogEntry {
  id: string;
  brand: string;
  model: string;
  soc: string;
  storage_type: 'eMMC' | 'UFS';
  msm_hwid: string;
  pk_hash: string;
  file_name: string;
  relative_path: string;
  file_size: number;
}

const DEFAULT_GITHUB_LOADER_URL = 'https://raw.githubusercontent.com/bkerler/Loaders/master/';

const MODEL_MAP_ENTRIES: LoaderCatalogEntry[] = [
  { id: 'qc_msm8953_xiaomi_generic', brand: 'Xiaomi', model: 'Redmi Note 4X / Redmi 4 Prime (mido)', soc: 'MSM8953 (Snapdragon 625)', storage_type: 'eMMC', msm_hwid: '0x0005E0E1', pk_hash: '*', file_name: 'prog_emmc_firehose_8953_ddr.mbn', relative_path: 'Xiaomi/Redmi_Note_4X/prog_emmc_firehose_8953_ddr.mbn', file_size: 284560 },
  { id: 'qc_sm6125_xiaomi_ginkgo', brand: 'Xiaomi', model: 'Redmi Note 8 / 8T (ginkgo/willow)', soc: 'SM6125 (Snapdragon 665)', storage_type: 'eMMC', msm_hwid: '0x000E10E1', pk_hash: '*', file_name: 'prog_emmc_firehose_sm6125_ddr.elf', relative_path: 'Xiaomi/Redmi_Note_8/prog_emmc_firehose_sm6125_ddr.elf', file_size: 1154304 },
  { id: 'qc_sm6225_xiaomi_spes', brand: 'Xiaomi', model: 'Redmi Note 11 4G (spes)', soc: 'SM6225 (Snapdragon 680)', storage_type: 'UFS', msm_hwid: '0x001700E1', pk_hash: '*', file_name: 'prog_firehose_lite_sm6225_ufs.elf', relative_path: 'Xiaomi/Redmi_Note_11/prog_firehose_lite_sm6225_ufs.elf', file_size: 1582080 },
  { id: 'qc_sm7125_xiaomi_curtana', brand: 'Xiaomi', model: 'Redmi Note 9 Pro / 9S (curtana)', soc: 'SM7125 (Snapdragon 720G)', storage_type: 'UFS', msm_hwid: '0x000A20E1', pk_hash: '*', file_name: 'prog_ufs_firehose_sm7125_ddr.elf', relative_path: 'Xiaomi/Redmi_Note_9_Pro/prog_ufs_firehose_sm7125_ddr.elf', file_size: 1640960 },
  { id: 'qc_sm7325_xiaomi_lisa', brand: 'Xiaomi', model: 'Xiaomi 11 Lite 5G NE (lisa)', soc: 'SM7325 (Snapdragon 778G)', storage_type: 'UFS', msm_hwid: '0x0013B0E1', pk_hash: '*', file_name: 'prog_ufs_firehose_sm7325_ddr.elf', relative_path: 'Xiaomi/Xiaomi_11_Lite_5G_NE/prog_ufs_firehose_sm7325_ddr.elf', file_size: 1720320 },
  { id: 'qc_sdm660_xiaomi_lavender', brand: 'Xiaomi', model: 'Redmi Note 7 (lavender)', soc: 'SDM660 (Snapdragon 660)', storage_type: 'eMMC', msm_hwid: '0x0006F0E1', pk_hash: '*', file_name: 'prog_emmc_firehose_sdm660_ddr.mbn', relative_path: 'Xiaomi/Redmi_Note_7/prog_emmc_firehose_sdm660_ddr.mbn', file_size: 394240 },
  { id: 'qc_msm8937_xiaomi_santoni', brand: 'Xiaomi', model: 'Redmi 4X (santoni)', soc: 'MSM8937 (Snapdragon 435)', storage_type: 'eMMC', msm_hwid: '0x000430E1', pk_hash: '*', file_name: 'prog_emmc_firehose_8937_ddr.mbn', relative_path: 'Xiaomi/Redmi_4X/prog_emmc_firehose_8937_ddr.mbn', file_size: 256000 },
  { id: 'qc_sm8250_xiaomi_alioth', brand: 'Poco', model: 'Poco F3 / Redmi K40 (alioth)', soc: 'SM8250 (Snapdragon 870)', storage_type: 'UFS', msm_hwid: '0x000BE0E1', pk_hash: '*', file_name: 'prog_ufs_firehose_sm8250_ddr.elf', relative_path: 'Xiaomi/Poco_F3/prog_ufs_firehose_sm8250_ddr.elf', file_size: 1824768 },
  { id: 'qc_sm6115_oppo_a53', brand: 'Oppo', model: 'Oppo A53 2020 (CPH2127)', soc: 'SM6115 (Snapdragon 460)', storage_type: 'UFS', msm_hwid: '0x000E10E1', pk_hash: '3be970a8579cac80', file_name: 'prog_ufs_firehose_sm6115_oppo.elf', relative_path: 'Oppo/Oppo_A53/prog_ufs_firehose_sm6115_oppo.elf', file_size: 1344512 },
  { id: 'qc_sm7250_oneplus_nord', brand: 'OnePlus', model: 'OnePlus Nord (AC2001/AC2003)', soc: 'SM7250 (Snapdragon 765G)', storage_type: 'UFS', msm_hwid: '0x000D80E1', pk_hash: '*', file_name: 'prog_ufs_firehose_sm7250_oneplus.elf', relative_path: 'OnePlus/OnePlus_Nord/prog_ufs_firehose_sm7250_oneplus.elf', file_size: 1690112 },
  { id: 'qc_sdm450_samsung_a20s', brand: 'Samsung', model: 'Galaxy A20s (SM-A207F/M)', soc: 'SDM450 (Snapdragon 450)', storage_type: 'eMMC', msm_hwid: '0x000660E1', pk_hash: '*', file_name: 'prog_emmc_firehose_sdm450_samsung.mbn', relative_path: 'Samsung/Galaxy_A20s/prog_emmc_firehose_sdm450_samsung.mbn', file_size: 384000 },
  { id: 'qc_sm6225_samsung_a23', brand: 'Samsung', model: 'Galaxy A23 4G (SM-A235F/M)', soc: 'SM6225 (Snapdragon 680)', storage_type: 'eMMC', msm_hwid: '0x001700E1', pk_hash: '*', file_name: 'prog_emmc_firehose_sm6225_samsung.elf', relative_path: 'Samsung/Galaxy_A23/prog_emmc_firehose_sm6225_samsung.elf', file_size: 1490944 },
  { id: 'qc_sm8450_samsung_s22', brand: 'Samsung', model: 'Galaxy S22 Series (SM-S901U)', soc: 'SM8450 (Snapdragon 8 Gen 1)', storage_type: 'UFS', msm_hwid: '0x001700E1', pk_hash: '*', file_name: 'prog_ufs_firehose_sm8450_samsung.elf', relative_path: 'Samsung/Galaxy_S22/prog_ufs_firehose_sm8450_samsung.elf', file_size: 1948672 },
  { id: 'qc_msm8953_vivo_v9', brand: 'Vivo', model: 'Vivo V9 / Y85 (1723)', soc: 'MSM8953 (Snapdragon 626)', storage_type: 'eMMC', msm_hwid: '0x0005E0E1', pk_hash: '*', file_name: 'prog_emmc_firehose_8953_vivo.mbn', relative_path: 'Vivo/Vivo_V9/prog_emmc_firehose_8953_vivo.mbn', file_size: 298496 },
];

// Scatter / Partition presets
const SAMPLE_PARTITIONS: PartitionItem[] = [
  { name: 'preloader', filename: 'preloader_k6833.bin', sizeMB: 0.5, offsetHex: '0x00000000', selected: true, status: 'idle', progress: 0 },
  { name: 'boot', filename: 'boot.img', sizeMB: 64, offsetHex: '0x00400000', selected: true, status: 'idle', progress: 0 },
  { name: 'recovery', filename: 'recovery.img', sizeMB: 64, offsetHex: '0x04400000', selected: true, status: 'idle', progress: 0 },
  { name: 'dtbo', filename: 'dtbo.img', sizeMB: 8, offsetHex: '0x08400000', selected: true, status: 'idle', progress: 0 },
  { name: 'vbmeta', filename: 'vbmeta.img', sizeMB: 4, offsetHex: '0x08C00000', selected: true, status: 'idle', progress: 0 },
  { name: 'vbmeta_system', filename: 'vbmeta_system.img', sizeMB: 4, offsetHex: '0x09000000', selected: true, status: 'idle', progress: 0 },
  { name: 'nvram', filename: 'nvram.bin', sizeMB: 5, offsetHex: '0x09400000', selected: false, status: 'idle', progress: 0 },
  { name: 'nvdata', filename: 'nvdata.img', sizeMB: 64, offsetHex: '0x09900000', selected: false, status: 'idle', progress: 0 },
  { name: 'persist', filename: 'persist.img', sizeMB: 48, offsetHex: '0x0D900000', selected: false, status: 'idle', progress: 0 },
  { name: 'super', filename: 'super.img (system+vendor+product)', sizeMB: 4608, offsetHex: '0x10900000', selected: true, status: 'idle', progress: 0 },
  { name: 'userdata', filename: 'userdata.img', sizeMB: 28672, offsetHex: '0x130900000', selected: false, status: 'idle', progress: 0 },
];

const BRANDS = [
  { id: 'xiaomi', name: 'XIAOMI / REDMI', models: ['Redmi 9 (MT6768 Helio G80)', 'Redmi 9A / 9C (MT6765 Helio G25/G35)', 'Poco M3 Pro 5G (MT6833 Dimensity 700)', 'Redmi Note 10 5G (MT6833)'] },
  { id: 'oppo', name: 'OPPO / REALME', models: ['Realme C11 / C15 (MT6765)', 'Realme 7 / Narzo 20 Pro (MT6785)', 'Oppo A15 / A16 (MT6765)', 'Oppo Reno 6Z (MT6853)'] },
  { id: 'vivo', name: 'VIVO / IQOO', models: ['Vivo Y12 / Y15 / Y17 (MT6765)', 'Vivo Y20 / Y21 (MT6765)', 'Vivo Y72 5G (MT6833)'] },
  { id: 'samsung', name: 'SAMSUNG MTK', models: ['Galaxy A02 / A12 (MT6739 / MT6765)', 'Galaxy A22 5G (MT6833)', 'Galaxy A32 4G (MT6768)'] },
  { id: 'generic', name: 'GENERIC MEDIATEK', models: ['Auto Detect MT67xx / MT68xx BROM', 'Helio G80/G85/G90/G95', 'Dimensity 700 / 810 / 900'] },
];

interface LogEntry {
  id: string;
  time: string;
  type: 'INFO' | 'SUCCESS' | 'WARNING' | 'HEX' | 'ERROR';
  text: string;
}

export default function App() {
  // Styling customizer states (Live tuning to address user's feedback)
  const [themeMode, setThemeMode] = useState<'unlocktool' | 'hydra' | 'chimera' | 'octoplus'>('unlocktool');
  const [cornerRadius, setCornerRadius] = useState<'sharp' | 'hardware' | 'bubbly'>('sharp'); // 2px vs 4px vs 10px
  const [buttonSize, setButtonSize] = useState<'compact' | 'medium' | 'bulky'>('compact'); // 30px vs 36px vs 46px
  const [borderStyle, setBorderStyle] = useState<'hairline' | 'glow' | 'solid'>('hairline');
  const [viewTab, setViewTab] = useState<'preview' | 'license' | 'comparison' | 'code' | 'discussion'>('preview');

  // License & Security Gate simulation states
  const [gateSubTab, setGateSubTab] = useState<'activate' | 'restore' | 'admin'>('activate');
  const [googleUser, setGoogleUser] = useState<{ email: string; name: string } | null>(null);
  const [regUsername, setRegUsername] = useState('Myanmar_GSM_Pro');
  const [regEmail, setRegEmail] = useState('');
  const [regPassword, setRegPassword] = useState('');
  const [regShowPassword, setRegShowPassword] = useState(false);
  const [regLicenseKey, setRegLicenseKey] = useState('MTK-0030-BROM-LIVE-88991122');
  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginShowPassword, setLoginShowPassword] = useState(false);
  const [gateAlarm, setGateAlarm] = useState<{ type: 'success' | 'error' | 'warning'; message: string } | null>({
    type: 'warning',
    message: 'Single Device Binding: License is strictly bound to this unique Hardware ID (HWID: MTK-8A00-6768-D9E2). Keys cannot be shared.'
  });
  const [activeSession, setActiveSession] = useState<{
    unlocked: boolean;
    user: string;
    key: string;
    type: string;
    remainingDays: number;
    hwid: string;
  } | null>(null);

  // Real-time USB Phone Connection Mode state (Adb, Fastboot, Brom, Preloader, Meta, Diag, Mtp)
  type TargetUsbMode = 'BROM' | 'PRELOADER' | 'META' | 'FASTBOOT' | 'ADB' | 'EDL' | 'DIAG' | 'MTP' | 'NO DEVICE';
  const [realtimeUsbMode, setRealtimeUsbMode] = useState<TargetUsbMode>('BROM');
  const [isActionLocked, setIsActionLocked] = useState(false);
  const [lockedActionName, setLockedActionName] = useState('');

  const switchRealtimeMode = (newMode: TargetUsbMode) => {
    if (isActionLocked && newMode !== realtimeUsbMode && newMode !== 'NO DEVICE') {
      setLogs(prev => [
        ...prev,
        { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'WARNING', text: `[PORT ISOLATION] Action '${lockedActionName}' in progress: locked to ${realtimeUsbMode}. Foreign port (${newMode}) ignored!` }
      ]);
      return;
    }
    setRealtimeUsbMode(newMode);
    if (newMode === 'NO DEVICE') {
      setLogs(prev => [
        ...prev,
        { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'WARNING', text: '[USB DETACH] Phone cable unplugged. Real-time port: DISCONNECTED.' }
      ]);
    } else {
      setLogs(prev => [
        ...prev,
        { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'SUCCESS', text: `[USB ATTACH] Real-time hardware detected: Phone in ${newMode} Mode.` }
      ]);
    }
  };

  const cycleRealtimeMode = () => {
    const modes: TargetUsbMode[] = ['BROM', 'PRELOADER', 'META', 'FASTBOOT', 'ADB', 'EDL', 'DIAG', 'MTP', 'NO DEVICE'];
    const nextIdx = (modes.indexOf(realtimeUsbMode) + 1) % modes.length;
    switchRealtimeMode(modes[nextIdx]);
  };

  // Interactive device state
  const [platformMode, setPlatformMode] = useState<'MTK' | 'QC'>('MTK');
  const [selectedBrandIndex, setSelectedBrandIndex] = useState(0);
  const [selectedModel, setSelectedModel] = useState(BRANDS[0].models[0]);
  const [scatterLoaded, setScatterLoaded] = useState(true);
  const [partitions, setPartitions] = useState<PartitionItem[]>(SAMPLE_PARTITIONS);
  const [flashingState, setFlashingState] = useState<'idle' | 'flashing' | 'done'>('idle');
  const [flashProgress, setFlashProgress] = useState(0);
  const [currentActionText, setCurrentActionText] = useState('Idle / Device Ready (BROM Mode)');
  const [copiedKey, setCopiedKey] = useState<string | null>(null);

  // Dual-Mode (Online Live-Stream vs Offline Local Sync) + bkerler/Loaders Catalog State
  const [operatingMode, setOperatingMode] = useState<'ONLINE' | 'OFFLINE'>('ONLINE');
  const [showStartupModeModal, setShowStartupModeModal] = useState(false);
  const [showLoaderCatalogModal, setShowLoaderCatalogModal] = useState(false);
  const [repoUrl, setRepoUrl] = useState(DEFAULT_GITHUB_LOADER_URL);
  const [repoUrlInput, setRepoUrlInput] = useState(DEFAULT_GITHUB_LOADER_URL);
  const [loaderSearch, setLoaderSearch] = useState('');
  const [loaderBrandFilter, setLoaderBrandFilter] = useState('ALL');
  const [matchedQcLoader, setMatchedQcLoader] = useState<LoaderCatalogEntry | null>(MODEL_MAP_ENTRIES[1]);
  const [syncedIds, setSyncedIds] = useState<string[]>([]);
  const [isSyncingLoaders, setIsSyncingLoaders] = useState(false);
  const [syncPercent, setSyncPercent] = useState(0);
  const [syncCurrentFile, setSyncCurrentFile] = useState('');
  const [lastSyncTime, setLastSyncTime] = useState('Never');
  const syncIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const [logs, setLogs] = useState<LogEntry[]>([
    { id: '1', time: '12:00:01', type: 'INFO', text: 'MTK Multi-USB Service Engine v4.0.0 Ready.' },
    { id: '2', time: '12:00:01', type: 'SUCCESS', text: '[+] JNI Native Driver libjin_usb.so initialized.' },
    { id: '3', time: '12:00:02', type: 'SUCCESS', text: '[+] Device DETECTED: MediaTek Inc. (VID: 0x0E8D PID: 0x0003) in BROM mode.' },
    { id: '4', time: '12:00:02', type: 'HEX', text: '[BROM] Handshake Sync Locked -> (0x5F 0xF5 0xAF 0xFA)' },
    { id: '5', time: '12:00:03', type: 'INFO', text: '[HW INFO] HW Code: 0x0707 (MT6768 Helio G80) • SubCode: 0x8A00 • Ver: 0xCA00' },
    { id: '6', time: '12:00:03', type: 'SUCCESS', text: '[+] Kamakiri2 BROM Security Exploit passed! SLA/DAA Watchdog disabled.' },
  ]);

  const terminalEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (terminalEndRef.current) {
      terminalEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs]);

  // Derived style parameters based on user selections
  const activeTheme = useMemo(() => {
    switch (themeMode) {
      case 'unlocktool':
        return {
          id: 'unlocktool',
          name: 'UnlockTool Laser Cyan',
          bg: 'bg-[#07090E]',
          surface: 'bg-[#0E131C]',
          surfaceElevated: 'bg-[#131A26]',
          border: 'border-[#1C2536]',
          accentText: 'text-[#00E5FF]',
          accentBg: 'bg-[#00E5FF]',
          glowBorder: 'border-[#00E5FF]/50 shadow-[0_0_12px_rgba(0,229,255,0.25)]',
          buttonPrimary: 'bg-gradient-to-r from-[#00A3FF] to-[#00E5FF] hover:from-[#008AE6] hover:to-[#00D0EB] text-slate-950 font-black shadow-[0_2px_10px_rgba(0,229,255,0.3)]',
          buttonAction: 'bg-gradient-to-b from-[#182233] to-[#0E1522] hover:from-[#212E44] hover:to-[#141C2B] text-slate-200 border border-[#26354D]',
          buttonDanger: 'bg-gradient-to-r from-[#FF1744] to-[#D50000] text-white font-bold shadow-[0_2px_8px_rgba(255,23,68,0.3)]',
          terminalBg: 'bg-[#030508] border-[#16202E] text-[#00E5FF]',
          tag: 'bg-[#00E5FF]/10 text-[#00E5FF] border-[#00E5FF]/30'
        };
      case 'hydra':
        return {
          id: 'hydra',
          name: 'Hydra Dark Violet',
          bg: 'bg-[#0A070E]',
          surface: 'bg-[#140F1E]',
          surfaceElevated: 'bg-[#1C152A]',
          border: 'border-[#2D1C3D]',
          accentText: 'text-[#E040FB]',
          accentBg: 'bg-[#E040FB]',
          glowBorder: 'border-[#E040FB]/50 shadow-[0_0_12px_rgba(224,64,251,0.25)]',
          buttonPrimary: 'bg-gradient-to-r from-[#D500F9] to-[#E040FB] hover:from-[#AA00FF] hover:to-[#D500F9] text-white font-black shadow-[0_2px_10px_rgba(224,64,251,0.3)]',
          buttonAction: 'bg-gradient-to-b from-[#221733] to-[#140C22] hover:from-[#2E1F45] hover:to-[#1C112E] text-slate-200 border border-[#3E255C]',
          buttonDanger: 'bg-gradient-to-r from-[#FF1744] to-[#C51162] text-white font-bold',
          terminalBg: 'bg-[#050308] border-[#251538] text-[#E040FB]',
          tag: 'bg-[#E040FB]/10 text-[#E040FB] border-[#E040FB]/30'
        };
      case 'chimera':
        return {
          id: 'chimera',
          name: 'Chimera Emerald Titanium',
          bg: 'bg-[#050806]',
          surface: 'bg-[#0B130F]',
          surfaceElevated: 'bg-[#111F19]',
          border: 'border-[#182C22]',
          accentText: 'text-[#00E676]',
          accentBg: 'bg-[#00E676]',
          glowBorder: 'border-[#00E676]/50 shadow-[0_0_12px_rgba(0,230,118,0.25)]',
          buttonPrimary: 'bg-gradient-to-r from-[#00C853] to-[#00E676] hover:from-[#009624] hover:to-[#00C853] text-slate-950 font-black shadow-[0_2px_10px_rgba(0,230,118,0.3)]',
          buttonAction: 'bg-gradient-to-b from-[#14261E] to-[#0A1610] hover:from-[#1C362A] hover:to-[#0F2018] text-slate-200 border border-[#214233]',
          buttonDanger: 'bg-gradient-to-r from-[#FF1744] to-[#D50000] text-white font-bold',
          terminalBg: 'bg-[#020503] border-[#11241A] text-[#00E676]',
          tag: 'bg-[#00E676]/10 text-[#00E676] border-[#00E676]/30'
        };
      case 'octoplus':
        return {
          id: 'octoplus',
          name: 'Octoplus Carbon Red',
          bg: 'bg-[#0C0708]',
          surface: 'bg-[#160D10]',
          surfaceElevated: 'bg-[#201317]',
          border: 'border-[#331B21]',
          accentText: 'text-[#FF1744]',
          accentBg: 'bg-[#FF1744]',
          glowBorder: 'border-[#FF1744]/50 shadow-[0_0_12px_rgba(255,23,68,0.25)]',
          buttonPrimary: 'bg-gradient-to-r from-[#D50000] to-[#FF1744] hover:from-[#B71C1C] hover:to-[#D50000] text-white font-black shadow-[0_2px_10px_rgba(255,23,68,0.3)]',
          buttonAction: 'bg-gradient-to-b from-[#26151A] to-[#160B0E] hover:from-[#361E25] hover:to-[#201015] text-slate-200 border border-[#44222B]',
          buttonDanger: 'bg-gradient-to-r from-[#FF1744] to-[#990000] text-white font-bold',
          terminalBg: 'bg-[#060304] border-[#2A1318] text-[#FF5252]',
          tag: 'bg-[#FF1744]/10 text-[#FF1744] border-[#FF1744]/30'
        };
    }
  }, [themeMode]);

  // Corner radius class generator
  const radiusClass = useMemo(() => {
    switch (cornerRadius) {
      case 'sharp': // 2px-3px sharp hardware bevel
        return {
          card: 'rounded-[3px]',
          button: 'rounded-[3px]',
          chip: 'rounded-[2px]',
          tag: '3px Sharp Hardware Bevel'
        };
      case 'hardware': // 4px-5px sleek
        return {
          card: 'rounded-[5px]',
          button: 'rounded-[4px]',
          chip: 'rounded-[3px]',
          tag: '5px Sleek Bevel'
        };
      case 'bubbly': // 10px old Material 3 (the one user disliked)
        return {
          card: 'rounded-[14px]',
          button: 'rounded-[10px]',
          chip: 'rounded-full',
          tag: '14px Old Bubbly Material (Disliked)'
        };
    }
  }, [cornerRadius]);

  // Button size class generator
  const buttonSizeClass = useMemo(() => {
    switch (buttonSize) {
      case 'compact': // 30px compact high density
        return {
          height: 'h-[30px]',
          padding: 'px-2.5 py-1',
          text: 'text-[11px]',
          icon: 'w-3.5 h-3.5',
          label: '30px Compact Tactile (High Density)'
        };
      case 'medium': // 36px balanced
        return {
          height: 'h-[36px]',
          padding: 'px-3 py-1.5',
          text: 'text-xs',
          icon: 'w-4 h-4',
          label: '36px Standard Hardware Key'
        };
      case 'bulky': // 46px old Material bulky
        return {
          height: 'h-[46px]',
          padding: 'px-4 py-2.5',
          text: 'text-sm font-semibold',
          icon: 'w-5 h-5',
          label: '46px Bulky Material Button (Disliked)'
        };
    }
  }, [buttonSize]);

  // Border style
  const borderClass = useMemo(() => {
    if (borderStyle === 'glow') return activeTheme.glowBorder;
    if (borderStyle === 'hairline') return `${activeTheme.border} border-[1px] shadow-sm`;
    return `${activeTheme.border} border-[1.5px]`;
  }, [borderStyle, activeTheme]);

  // Operations execution simulator with strict action isolation lock
  const executeOperation = (name: string, hexCode: string) => {
    setIsActionLocked(true);
    setLockedActionName(name);
    setCurrentActionText(`Executing: ${name}... (Port Locked to ${realtimeUsbMode})`);
    setLogs(prev => [
      ...prev,
      { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'INFO', text: `>>> Initiating Command: ${name} [${hexCode}] • [ACTION LOCKED: ${realtimeUsbMode}]` },
    ]);

    setTimeout(() => {
      setLogs(prev => [
        ...prev,
        { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'HEX', text: `[USB OUT] URB Endpoint 0x01 Write -> 16 bytes (${hexCode}) OK` },
      ]);
    }, 250);

    setTimeout(() => {
      setLogs(prev => [
        ...prev,
        { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'SUCCESS', text: `[+] ${name} COMPLETED SUCCESSFULLY! Status: 0x00 (SUCCESS)` },
      ]);
      setCurrentActionText(`Finished: ${name}`);
      setIsActionLocked(false);
      setLockedActionName('');
    }, 700);
  };

  const startFlashSelected = () => {
    setIsActionLocked(true);
    setLockedActionName('Flashing Firmware');
    setFlashingState('flashing');
    setFlashProgress(0);
    setCurrentActionText(`Computing streaming CRC32 checksum for super.img (Port Locked to ${realtimeUsbMode})...`);
    
    setLogs(prev => [
      ...prev,
      { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'INFO', text: `>>> [ACTION LOCKED] Flash operation initiated on ${realtimeUsbMode}. All foreign USB ports isolated.` },
      { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'SUCCESS', text: '[CRC32 READY] Checksum: 0x7B9A2F41 (Computed in 1,120 ms, Heap: 128 KB, 0 OOM Risk)' },
      { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'INFO', text: '[DA PROTOCOL] Negotiated Direct Sparse Download Command with CRC32 Verification (2.5x Speed)' }
    ]);

    let prog = 0;
    const interval = setInterval(() => {
      prog += 5;
      if (prog >= 100) {
        clearInterval(interval);
        setFlashProgress(100);
        setFlashingState('done');
        setIsActionLocked(false);
        setLockedActionName('');
        setCurrentActionText('Flash Complete • CRC32 & SHA-256 Verified 100% OK');
        setLogs(prev => [
          ...prev,
          { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'SUCCESS', text: '[+] ALL SELECTED PARTITIONS FLASHED & VERIFIED 100% OK!' },
          { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'INFO', text: '[+] Total Time: 48s (Saved 180s vs raw unsparse write). Rebooting device...' }
        ]);
      } else {
        setFlashProgress(prog);
      }
    }, 150);
  };

  const togglePartition = (index: number) => {
    setPartitions(prev => {
      const next = [...prev];
      next[index].selected = !next[index].selected;
      return next;
    });
  };

  const copyCode = (key: string, code: string) => {
    navigator.clipboard.writeText(code);
    setCopiedKey(key);
    setTimeout(() => setCopiedKey(null), 2000);
  };

  const appendLog = (type: LogEntry['type'], text: string) => {
    setLogs(prev => [
      ...prev,
      { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type, text }
    ]);
  };

  const startOfflineLoaderSync = () => {
    if (isSyncingLoaders) return;
    const missing = MODEL_MAP_ENTRIES.filter(e => !syncedIds.includes(e.id));
    if (missing.length === 0) {
      appendLog('SUCCESS', `[OFFLINE SYNC] All ${MODEL_MAP_ENTRIES.length} Firehose loaders already synced in local storage.`);
      return;
    }
    setIsSyncingLoaders(true);
    setSyncPercent(0);
    appendLog('INFO', `[OFFLINE SYNC] Downloading model_map.json + ${missing.length} binaries from ${repoUrl}`);
    let idx = 0;
    syncIntervalRef.current = setInterval(() => {
      const current = missing[idx];
      if (!current) {
        if (syncIntervalRef.current) clearInterval(syncIntervalRef.current);
        setIsSyncingLoaders(false);
        setSyncPercent(100);
        setSyncCurrentFile('');
        setLastSyncTime(new Date().toLocaleTimeString());
        appendLog('SUCCESS', `[+] Offline database ready (${MODEL_MAP_ENTRIES.length}/${MODEL_MAP_ENTRIES.length} loaders saved to /NativeUnlockTool/loaders/).`);
        return;
      }
      setSyncCurrentFile(current.relative_path);
      setSyncedIds(prev => (prev.includes(current.id) ? prev : [...prev, current.id]));
      idx += 1;
      setSyncPercent(Math.round((idx / missing.length) * 100));
    }, 180);
  };

  const cancelOfflineLoaderSync = () => {
    if (syncIntervalRef.current) clearInterval(syncIntervalRef.current);
    setIsSyncingLoaders(false);
    setSyncCurrentFile('');
    appendLog('WARNING', '[OFFLINE SYNC] Cancelled by user.');
  };

  const switchAppOperatingMode = (mode: 'ONLINE' | 'OFFLINE', triggerSync = false) => {
    setOperatingMode(mode);
    setShowStartupModeModal(false);
    appendLog(
      'INFO',
      mode === 'ONLINE'
        ? `[MODE] ONLINE Live-Stream active (0 MB storage footprint · Live repo: ${repoUrl})`
        : '[MODE] OFFLINE Local-Sync active (Reads /NativeUnlockTool/loaders/ + flat HWID_PKHASH)'
    );
    if (mode === 'OFFLINE' && triggerSync) {
      startOfflineLoaderSync();
    }
  };

  const simulateQcSaharaAutoMatch = () => {
    const target = matchedQcLoader || MODEL_MAP_ENTRIES[1];
    switchRealtimeMode('EDL');
    setCurrentActionText(`Sahara Auto-Match (${target.msm_hwid})`);
    appendLog('INFO', '[EDL 9008] Sahara Protocol Handshake -> CMD 0x02 (Read MSM_HWID) & CMD 0x03 (Read PK_HASH)...');
    appendLog('HEX', `[SAHARA] MSM_HWID: ${target.msm_hwid} · PK_HASH: ${target.pk_hash === '*' ? '3BE970A8579CAC80...' : target.pk_hash}`);
    setTimeout(() => {
      if (operatingMode === 'ONLINE') {
        appendLog('INFO', `[ONLINE RESOLVER] Matched ${target.brand} ${target.model} (${target.soc}) in model_map.json.`);
        appendLog('SUCCESS', `[LIVE STREAM] Streaming ${(target.file_size / 1024).toFixed(0)} KB -> RAM (${target.relative_path}) -> Sahara Upload OK!`);
      } else {
        const isLocal = syncedIds.includes(target.id);
        if (isLocal) {
          appendLog('SUCCESS', `[OFFLINE HIT] Loaded /NativeUnlockTool/loaders/${target.relative_path} -> Sahara Upload OK!`);
        } else {
          appendLog('WARNING', `[OFFLINE MISS] ${target.file_name} not yet synced locally. Click "Sync Offline" or switch to ONLINE Live Stream.`);
        }
      }
    }, 320);
  };

  const filteredLoaderEntries = useMemo(() => {
    const q = loaderSearch.trim().toLowerCase();
    return MODEL_MAP_ENTRIES.filter(entry => {
      const brandOk = loaderBrandFilter === 'ALL' || entry.brand === loaderBrandFilter;
      const queryOk =
        !q ||
        entry.brand.toLowerCase().includes(q) ||
        entry.model.toLowerCase().includes(q) ||
        entry.soc.toLowerCase().includes(q) ||
        entry.msm_hwid.toLowerCase().includes(q) ||
        entry.file_name.toLowerCase().includes(q);
      return brandOk && queryOk;
    });
  }, [loaderSearch, loaderBrandFilter]);

  return (
    <div className={`min-h-screen ${activeTheme.bg} text-slate-100 flex flex-col font-sans transition-colors duration-200 select-none`}>
      
      {/* Top Bar: Brand Title & Navigation Tabs */}
      <header className="border-b border-slate-800/80 bg-[#07090D] px-4 py-2.5 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded bg-gradient-to-br from-cyan-400 to-blue-600 flex items-center justify-center font-black text-slate-950 text-sm shadow-md shadow-cyan-500/20">
            MZ
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-mono font-black text-white text-sm tracking-wider">
                MAS ZAR NI GSM SERVICE BOX
              </span>
              <span className={`text-[10px] font-mono font-bold px-2 py-0.2 border ${activeTheme.tag} rounded`}>
                PRO HARDWARE UI LAB
              </span>
            </div>
            <p className="text-[11px] text-slate-400">
              Interactive Design System: Custom Colors • Sharp Card Forms • Tactile Compact Buttons
            </p>
          </div>
        </div>

        {/* View Switcher Tabs */}
        <div className="flex items-center gap-1.5 bg-slate-950 border border-slate-800 p-1 rounded">
          <button
            onClick={() => setViewTab('preview')}
            className={`px-3 py-1 rounded text-xs font-mono font-bold transition flex items-center gap-1.5 ${
              viewTab === 'preview'
                ? 'bg-slate-800 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Smartphone className="w-3.5 h-3.5 text-cyan-400" />
            <span>၁။ Android Mockup</span>
          </button>

          <button
            onClick={() => setViewTab('license')}
            className={`px-3 py-1 rounded text-xs font-mono font-bold transition flex items-center gap-1.5 ${
              viewTab === 'license'
                ? 'bg-slate-800 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Lock className="w-3.5 h-3.5 text-amber-400" />
            <span>၂။ License & APK Security Gate</span>
          </button>

          <button
            onClick={() => setViewTab('comparison')}
            className={`px-3 py-1 rounded text-xs font-mono font-bold transition flex items-center gap-1.5 ${
              viewTab === 'comparison'
                ? 'bg-slate-800 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Layers className="w-3.5 h-3.5 text-emerald-400" />
            <span>၃။ UI နှိုင်းယှဉ်ချက်</span>
          </button>

          <button
            onClick={() => setViewTab('code')}
            className={`px-3 py-1 rounded text-xs font-mono font-bold transition flex items-center gap-1.5 ${
              viewTab === 'code'
                ? 'bg-slate-800 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200'
            }`}
          >
            <Code2 className="w-3.5 h-3.5 text-purple-400" />
            <span>၄။ Compose Code</span>
          </button>
        </div>

        {/* Top-Right: Operating Mode (Online/Offline) + Real-time USB Mode Badge + Dark Mode Toggle */}
        <div className="flex items-center gap-2">
          <button
            onClick={() => setShowStartupModeModal(true)}
            className={`px-2.5 py-1.5 rounded text-xs font-mono font-bold flex items-center gap-1.5 border transition ${
              operatingMode === 'ONLINE'
                ? 'bg-cyan-950/60 border-cyan-500/70 text-cyan-300 hover:bg-cyan-950'
                : 'bg-emerald-950/60 border-emerald-500/70 text-emerald-300 hover:bg-emerald-950'
            }`}
            title="Switch between Online Live-Stream and Offline Local-Sync Mode"
          >
            {operatingMode === 'ONLINE' ? <Cloud className="w-3.5 h-3.5" /> : <HardDrive className="w-3.5 h-3.5" />}
            <span>{operatingMode === 'ONLINE' ? 'ONLINE LIVE' : `OFFLINE (${syncedIds.length}/${MODEL_MAP_ENTRIES.length})`}</span>
          </button>

          {/* USB Mode Badge next to Dark Mode button */}
          <button
            onClick={cycleRealtimeMode}
            className={`px-3 py-1.5 rounded text-xs font-mono font-black flex items-center gap-2 border transition ${
              realtimeUsbMode === 'BROM'
                ? 'bg-cyan-950/80 border-cyan-400 text-cyan-300'
                : realtimeUsbMode === 'PRELOADER'
                ? 'bg-amber-950/80 border-amber-400 text-amber-300'
                : realtimeUsbMode === 'FASTBOOT'
                ? 'bg-emerald-950/80 border-emerald-400 text-emerald-300'
                : realtimeUsbMode === 'ADB'
                ? 'bg-sky-950/80 border-sky-400 text-sky-300'
                : realtimeUsbMode === 'EDL'
                ? 'bg-blue-950/80 border-blue-400 text-blue-300'
                : realtimeUsbMode === 'META'
                ? 'bg-purple-950/80 border-purple-400 text-purple-300'
                : realtimeUsbMode === 'DIAG'
                ? 'bg-indigo-950/80 border-indigo-400 text-indigo-300'
                : realtimeUsbMode === 'MTP'
                ? 'bg-teal-950/80 border-teal-400 text-teal-300'
                : 'bg-rose-950/40 border-rose-800 text-rose-400'
            }`}
            title="Click to cycle USB Mode (BROM, Preloader, Meta, Fastboot, Adb, EDL, Diag, Mtp, Disconnected)"
          >
            <span
              className={`w-2 h-2 rounded-full ${
                realtimeUsbMode === 'NO DEVICE' ? 'bg-rose-500' : 'bg-emerald-400 animate-pulse'
              }`}
            />
            <span>{isActionLocked ? `🔒 ${realtimeUsbMode}` : realtimeUsbMode}</span>
          </button>

          {/* Dark Mode Button */}
          <button
            onClick={() => setThemeMode(themeMode === 'unlocktool' ? 'octoplus' : 'unlocktool')}
            className="w-8 h-8 rounded border border-slate-800 bg-slate-900 flex items-center justify-center text-amber-400 hover:text-amber-300 transition"
            title="Toggle Light or Dark Mode"
          >
            ☀️
          </button>
        </div>
      </header>

      {/* QUICK STYLE CONTROLLER DOCK (Live User Tuning Bar) */}
      <div className="bg-[#0A0E17] border-b border-slate-800 px-4 py-2.5">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          
          {/* Real-time Hardware Port Simulator */}
          <div className="flex flex-wrap items-center gap-1.5 font-mono text-[11px]">
            <span className="text-slate-400 font-bold flex items-center gap-1 mr-1">
              <Usb className="w-3.5 h-3.5 text-cyan-400" />
              <span>Real-Time Port:</span>
            </span>
            {(['BROM', 'PRELOADER', 'META', 'FASTBOOT', 'ADB', 'EDL', 'DIAG', 'MTP', 'NO DEVICE'] as TargetUsbMode[]).map(m => (
              <button
                key={m}
                onClick={() => switchRealtimeMode(m)}
                className={`px-2 py-0.5 rounded text-[10px] font-bold border transition ${
                  realtimeUsbMode === m
                    ? 'bg-cyan-600 text-white border-cyan-400 ring-1 ring-cyan-400'
                    : 'bg-slate-950 text-slate-400 border-slate-800 hover:text-white'
                }`}
              >
                {m === 'NO DEVICE' ? 'UNPLUG' : m}
              </button>
            ))}
          </div>
          
          {/* 1. Theme Color Selector */}
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-mono text-slate-400 font-bold uppercase flex items-center gap-1">
              <Palette className="w-3 h-3 text-cyan-400" />
              <span>Color Design:</span>
            </span>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setThemeMode('unlocktool')}
                className={`px-2.5 py-1 text-[11px] font-mono font-bold rounded transition border ${
                  themeMode === 'unlocktool'
                    ? 'bg-cyan-950 text-cyan-300 border-cyan-400 ring-1 ring-cyan-400/50'
                    : 'bg-slate-900 text-slate-400 border-slate-800 hover:border-slate-700'
                }`}
              >
                ⚡ Laser Cyan (UnlockTool)
              </button>

              <button
                onClick={() => setThemeMode('hydra')}
                className={`px-2.5 py-1 text-[11px] font-mono font-bold rounded transition border ${
                  themeMode === 'hydra'
                    ? 'bg-purple-950 text-purple-300 border-purple-400 ring-1 ring-purple-400/50'
                    : 'bg-slate-900 text-slate-400 border-slate-800 hover:border-slate-700'
                }`}
              >
                🔮 Dark Violet (Hydra)
              </button>

              <button
                onClick={() => setThemeMode('chimera')}
                className={`px-2.5 py-1 text-[11px] font-mono font-bold rounded transition border ${
                  themeMode === 'chimera'
                    ? 'bg-emerald-950 text-emerald-300 border-emerald-400 ring-1 ring-emerald-400/50'
                    : 'bg-slate-900 text-slate-400 border-slate-800 hover:border-slate-700'
                }`}
              >
                🟢 Emerald Titanium (Chimera)
              </button>

              <button
                onClick={() => setThemeMode('octoplus')}
                className={`px-2.5 py-1 text-[11px] font-mono font-bold rounded transition border ${
                  themeMode === 'octoplus'
                    ? 'bg-rose-950 text-rose-300 border-rose-400 ring-1 ring-rose-400/50'
                    : 'bg-slate-900 text-slate-400 border-slate-800 hover:border-slate-700'
                }`}
              >
                🔴 Carbon Red (Octoplus)
              </button>
            </div>
          </div>

          {/* 2. Card Corner Radius (Sharp vs Bubbly) */}
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-mono text-slate-400 font-bold uppercase">
              Card Corner Form:
            </span>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setCornerRadius('sharp')}
                className={`px-2 py-0.5 text-[11px] font-mono rounded transition border ${
                  cornerRadius === 'sharp'
                    ? 'bg-slate-800 text-cyan-300 border-cyan-400 font-bold'
                    : 'bg-slate-950 text-slate-400 border-slate-800'
                }`}
              >
                📐 Sharp 3px (Pro Box)
              </button>

              <button
                onClick={() => setCornerRadius('hardware')}
                className={`px-2 py-0.5 text-[11px] font-mono rounded transition border ${
                  cornerRadius === 'hardware'
                    ? 'bg-slate-800 text-cyan-300 border-cyan-400 font-bold'
                    : 'bg-slate-950 text-slate-400 border-slate-800'
                }`}
              >
                📏 5px Bevel
              </button>

              <button
                onClick={() => setCornerRadius('bubbly')}
                className={`px-2 py-0.5 text-[11px] font-mono rounded transition border ${
                  cornerRadius === 'bubbly'
                    ? 'bg-rose-950 text-rose-300 border-rose-400 font-bold'
                    : 'bg-slate-950 text-slate-400 border-slate-800'
                }`}
                title="This is the old rounded shape you disliked"
              >
                ⭕ 14px Old Bubbly (Disliked)
              </button>
            </div>
          </div>

          {/* 3. Button Height & Sizing */}
          <div className="flex items-center gap-2">
            <span className="text-[11px] font-mono text-slate-400 font-bold uppercase">
              Button Sizes:
            </span>
            <div className="flex items-center gap-1">
              <button
                onClick={() => setButtonSize('compact')}
                className={`px-2 py-0.5 text-[11px] font-mono rounded transition border ${
                  buttonSize === 'compact'
                    ? 'bg-slate-800 text-emerald-300 border-emerald-400 font-bold'
                    : 'bg-slate-950 text-slate-400 border-slate-800'
                }`}
              >
                🎯 30px Compact (High Density)
              </button>

              <button
                onClick={() => setButtonSize('medium')}
                className={`px-2 py-0.5 text-[11px] font-mono rounded transition border ${
                  buttonSize === 'medium'
                    ? 'bg-slate-800 text-cyan-300 border-cyan-400 font-bold'
                    : 'bg-slate-950 text-slate-400 border-slate-800'
                }`}
              >
                🎮 36px Hardware Key
              </button>

              <button
                onClick={() => setButtonSize('bulky')}
                className={`px-2 py-0.5 text-[11px] font-mono rounded transition border ${
                  buttonSize === 'bulky'
                    ? 'bg-rose-950 text-rose-300 border-rose-400 font-bold'
                    : 'bg-slate-950 text-slate-400 border-slate-800'
                }`}
                title="This is the old bulky size you disliked"
              >
                📱 46px Bulky Material (Disliked)
              </button>
            </div>
          </div>

        </div>
      </div>

      {/* MAIN CONTENT AREA */}
      <main className="flex-1 max-w-7xl w-full mx-auto p-4 md:p-6 space-y-4">
        
        {/* VIEW 1: INTERACTIVE ANDROID APP WORKBENCH (Pixel-Perfect Mockup) */}
        {viewTab === 'preview' && (
          <div className="space-y-4">
            
            {/* Real-time Status Indicator Strip */}
            <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} px-4 py-2 flex flex-wrap items-center justify-between gap-3 text-xs font-mono`}>
              <div className="flex items-center gap-3">
                <span className="flex items-center gap-1.5 text-emerald-400 font-bold">
                  <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse" />
                  USB OTG HOST ACTIVE
                </span>
                <span className="text-slate-600">|</span>
                <span className="text-slate-300">
                  Target: <strong className="text-white">MediaTek BROM (VID 0x0E8D PID 0x0003)</strong>
                </span>
                <span className="text-slate-600">|</span>
                <span className="text-slate-400">JNI Driver: <strong className="text-cyan-400">libjin_usb.so</strong></span>
              </div>

              <div className="flex items-center gap-3">
                <span className="text-slate-400">Status: <strong className={activeTheme.accentText}>{currentActionText}</strong></span>
                <span className={`px-2 py-0.5 text-[10px] font-bold ${activeTheme.tag} ${radiusClass.chip}`}>
                  ACTIVE CHASSIS
                </span>
              </div>
            </div>

            {/* Main Dual-Column Engineering Workbench */}
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
              
              {/* LEFT COLUMN: BRAND / MODEL + OPERATION CONTROLS (5 cols) */}
              <div className="lg:col-span-5 space-y-4">
                
                {/* Module 1: Platform Switcher + Brand & Model Selection */}
                <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-3.5 space-y-3`}>
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2 gap-2">
                    <span className="text-xs font-mono font-bold text-slate-300 uppercase flex items-center gap-1.5">
                      <Cpu className="w-3.5 h-3.5 text-cyan-400" />
                      <span>Device & Loader Engine</span>
                    </span>
                    <div className="flex items-center gap-1 bg-slate-950 border border-slate-800 p-0.5 rounded font-mono text-[10px]">
                      <button
                        onClick={() => setPlatformMode('MTK')}
                        className={`px-2 py-0.5 rounded font-bold transition ${
                          platformMode === 'MTK' ? 'bg-cyan-600 text-white' : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        MTK BROM
                      </button>
                      <button
                        onClick={() => setPlatformMode('QC')}
                        className={`px-2 py-0.5 rounded font-bold transition ${
                          platformMode === 'QC' ? 'bg-blue-600 text-white' : 'text-slate-400 hover:text-white'
                        }`}
                      >
                        QC EDL 9008
                      </button>
                    </div>
                  </div>

                  {platformMode === 'MTK' ? (
                    <>
                      {/* Brand Horizontal Chips */}
                      <div className="grid grid-cols-3 gap-1.5">
                        {BRANDS.map((brand, idx) => (
                          <button
                            key={brand.id}
                            onClick={() => {
                              setSelectedBrandIndex(idx);
                              setSelectedModel(brand.models[0]);
                            }}
                            className={`text-[11px] font-mono font-bold px-2 py-1.5 transition text-center truncate ${radiusClass.button} border ${
                              selectedBrandIndex === idx
                                ? `${activeTheme.buttonPrimary} border-transparent`
                                : `${activeTheme.surfaceElevated} text-slate-300 border-slate-800 hover:border-slate-700`
                            }`}
                          >
                            {brand.name.split(' ')[0]}
                          </button>
                        ))}
                      </div>

                      {/* Model Dropdown Selector */}
                      <div className="space-y-1">
                        <label className="text-[10px] font-mono text-slate-400 uppercase">Target Phone Chipset / Model:</label>
                        <div className="relative">
                          <select
                            value={selectedModel}
                            onChange={(e) => setSelectedModel(e.target.value)}
                            className={`w-full bg-[#080B12] text-xs font-mono text-white px-3 py-2 border border-slate-800 ${radiusClass.button} focus:outline-none focus:border-cyan-400 cursor-pointer appearance-none`}
                          >
                            {BRANDS[selectedBrandIndex].models.map((m) => (
                              <option key={m} value={m} className="bg-slate-900 text-white">
                                {m}
                              </option>
                            ))}
                          </select>
                          <ChevronDown className="w-3.5 h-3.5 text-slate-400 absolute right-3 top-2.5 pointer-events-none" />
                        </div>
                      </div>

                      {/* Scatter & Firmware Loaders */}
                      <div className="grid grid-cols-2 gap-2 pt-1">
                        <button
                          onClick={() => setScatterLoaded(!scatterLoaded)}
                          className={`flex items-center justify-center gap-1.5 text-xs font-mono font-bold ${buttonSizeClass.height} ${buttonSizeClass.padding} ${radiusClass.button} transition border ${
                            scatterLoaded
                              ? 'bg-emerald-950/60 text-emerald-400 border-emerald-500/40'
                              : `${activeTheme.surfaceElevated} text-slate-300 border-slate-700 hover:border-slate-600`
                          }`}
                        >
                          <FileUp className={buttonSizeClass.icon} />
                          <span className="truncate">{scatterLoaded ? 'MT6768_scatter.txt' : 'Load Scatter'}</span>
                        </button>

                        <button
                          className={`flex items-center justify-center gap-1.5 text-xs font-mono font-bold ${buttonSizeClass.height} ${buttonSizeClass.padding} ${radiusClass.button} ${activeTheme.surfaceElevated} text-slate-300 border border-slate-700 hover:border-slate-600 transition`}
                        >
                          <FolderOpen className={buttonSizeClass.icon} />
                          <span className="truncate">Load Firmware Dir</span>
                        </button>
                      </div>
                    </>
                  ) : (
                    /* QUALCOMM EDL + bkerler/Loaders (model_map.json) PANEL */
                    <div className="space-y-2.5 font-mono">
                      <div className="flex items-center justify-between text-[11px] bg-slate-950 border border-slate-800 px-2.5 py-2 rounded">
                        <div className="min-w-0">
                          <div className="text-[10px] text-slate-400">bkerler/Loaders · model_map.json v105</div>
                          <div className="text-white font-bold truncate">
                            {matchedQcLoader ? `${matchedQcLoader.brand} · ${matchedQcLoader.model}` : 'Auto-Detect by HWID'}
                          </div>
                          <div className="text-[10px] text-cyan-400 tabular-nums truncate">
                            {matchedQcLoader ? `HWID ${matchedQcLoader.msm_hwid} · ${matchedQcLoader.storage_type} · ${matchedQcLoader.file_name}` : 'Waiting for Sahara CMD 0x02/0x03'}
                          </div>
                        </div>
                        <button
                          onClick={() => setShowLoaderCatalogModal(true)}
                          className="shrink-0 px-2.5 py-1.5 rounded bg-cyan-950/60 border border-cyan-500/60 text-cyan-300 hover:bg-cyan-900/60 text-[10px] font-bold"
                        >
                          Browse ({MODEL_MAP_ENTRIES.length})
                        </button>
                      </div>

                      <div className="grid grid-cols-2 gap-2">
                        <button
                          onClick={simulateQcSaharaAutoMatch}
                          className={`flex items-center justify-center gap-1.5 text-xs font-bold ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonPrimary} transition`}
                        >
                          <Zap className="w-3.5 h-3.5" />
                          <span>Boot 9008 Auto-Match</span>
                        </button>
                        <button
                          onClick={() => setShowLoaderCatalogModal(true)}
                          className={`flex items-center justify-center gap-1.5 text-xs font-bold ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonAction} transition`}
                        >
                          <CloudDownload className="w-3.5 h-3.5 text-emerald-400" />
                          <span>{isSyncingLoaders ? `Syncing ${syncPercent}%` : 'Repo & Offline Sync'}</span>
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                {/* Module 2: Hardware Operation Action Keys (Tactile Hardware Form Buttons) */}
                <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-3.5 space-y-3`}>
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <span className="text-xs font-mono font-bold text-slate-300 uppercase flex items-center gap-1.5">
                      <Zap className="w-3.5 h-3.5 text-amber-400" />
                      <span>One-Click Service Operations</span>
                    </span>
                    <span className="text-[10px] font-mono text-emerald-400 font-bold">BROM DIRECT</span>
                  </div>

                  <div className="grid grid-cols-2 gap-2">
                    <button
                      onClick={() => executeOperation('BROM Handshake Sync', '0xA0 0x0A 0x50 0x05')}
                      className={`flex items-center justify-center gap-1.5 font-mono ${buttonSizeClass.text} ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonAction} font-bold transition shadow-sm`}
                    >
                      <Usb className={buttonSizeClass.icon} />
                      <span>BROM Handshake</span>
                    </button>

                    <button
                      onClick={() => executeOperation('Kamakiri2 SLA/DAA Bypass', 'WATCHDOG_PAYLOAD')}
                      className={`flex items-center justify-center gap-1.5 font-mono ${buttonSizeClass.text} ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonAction} font-bold transition shadow-sm`}
                    >
                      <ShieldCheck className={buttonSizeClass.icon} />
                      <span>Bypass SLA / DAA</span>
                    </button>

                    <button
                      onClick={() => executeOperation('Dump NVRAM & IMEI Calibration', 'READ_SECTORS 0x09400000')}
                      className={`flex items-center justify-center gap-1.5 font-mono ${buttonSizeClass.text} ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonAction} font-bold transition shadow-sm`}
                    >
                      <HardDrive className={buttonSizeClass.icon} />
                      <span>Dump NVRAM Backup</span>
                    </button>

                    <button
                      onClick={() => executeOperation('Erase FRP (Google Account Lock)', 'FORMAT_PARTITION frp')}
                      className={`flex items-center justify-center gap-1.5 font-mono ${buttonSizeClass.text} ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonAction} font-bold transition shadow-sm`}
                    >
                      <Lock className={buttonSizeClass.icon} />
                      <span>Format FRP Lock</span>
                    </button>

                    <button
                      onClick={() => executeOperation('Unlock Bootloader (SCFG Patch)', 'WRITE_SECCFG_MAGIC')}
                      className={`flex items-center justify-center gap-1.5 font-mono ${buttonSizeClass.text} ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonAction} font-bold transition shadow-sm`}
                    >
                      <Zap className={buttonSizeClass.icon} />
                      <span>Unlock Bootloader</span>
                    </button>

                    <button
                      onClick={() => executeOperation('Relock Bootloader', 'LOCK_SECCFG')}
                      className={`flex items-center justify-center gap-1.5 font-mono ${buttonSizeClass.text} ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonAction} font-bold transition shadow-sm`}
                    >
                      <ShieldCheck className={buttonSizeClass.icon} />
                      <span>Relock Bootloader</span>
                    </button>
                  </div>

                  {/* Primary Flashing Key & Stop Key */}
                  <div className="pt-2 border-t border-slate-800 grid grid-cols-3 gap-2">
                    <button
                      onClick={startFlashSelected}
                      disabled={flashingState === 'flashing'}
                      className={`col-span-2 flex items-center justify-center gap-2 font-mono ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonPrimary} text-xs font-black uppercase transition disabled:opacity-50`}
                    >
                      <Play className="w-4 h-4 fill-current" />
                      <span>{flashingState === 'flashing' ? `Writing ${flashProgress}%` : 'Write Selected Flash'}</span>
                    </button>

                    <button
                      onClick={() => {
                        setFlashingState('idle');
                        setCurrentActionText('Operation Halted by User');
                        setLogs(prev => [...prev, { id: Math.random().toString(), time: new Date().toLocaleTimeString(), type: 'WARNING', text: '[!] EMERGENCY STOP: Active USB transfer aborted.' }]);
                      }}
                      className={`flex items-center justify-center gap-1.5 font-mono ${buttonSizeClass.height} ${radiusClass.button} ${activeTheme.buttonDanger} text-xs font-bold uppercase transition`}
                    >
                      <span>Stop</span>
                    </button>
                  </div>
                </div>

              </div>

              {/* RIGHT COLUMN: SCATTER PARTITION TABLE & HARDWARE TERMINAL (7 cols) */}
              <div className="lg:col-span-7 space-y-4">
                
                {/* Module 3: High-Density Scatter Partition Grid */}
                <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-3.5 space-y-2.5`}>
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <div className="flex items-center gap-2">
                      <span className="text-xs font-mono font-bold text-slate-300 uppercase flex items-center gap-1.5">
                        <Database className="w-3.5 h-3.5 text-cyan-400" />
                        <span>Scatter Partition Table (GPT Layout)</span>
                      </span>
                      <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-slate-900 text-slate-400 border border-slate-800">
                        11 Partitions (33.4 GB)
                      </span>
                    </div>

                    <button
                      onClick={() => {
                        const allSelected = partitions.every(p => p.selected);
                        setPartitions(prev => prev.map(p => ({ ...p, selected: !allSelected })));
                      }}
                      className="text-[11px] font-mono text-cyan-400 hover:text-cyan-300 transition"
                    >
                      {partitions.every(p => p.selected) ? 'Deselect All' : 'Select All'}
                    </button>
                  </div>

                  {/* Partition Table View */}
                  <div className="border border-slate-800 rounded overflow-hidden max-h-56 overflow-y-auto bg-[#070A10]">
                    <table className="w-full text-left text-xs font-mono">
                      <thead className="bg-[#0C101A] text-slate-400 border-b border-slate-800 sticky top-0 text-[10px] uppercase">
                        <tr>
                          <th className="p-2 w-8 text-center">✓</th>
                          <th className="p-2">Name</th>
                          <th className="p-2">Image File</th>
                          <th className="p-2">Offset Hex</th>
                          <th className="p-2">Size</th>
                          <th className="p-2">State</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-slate-800/40 text-[11px]">
                        {partitions.map((part, idx) => (
                          <tr
                            key={part.name}
                            onClick={() => togglePartition(idx)}
                            className={`cursor-pointer transition-colors ${
                              part.selected
                                ? `${activeTheme.surfaceElevated} hover:bg-slate-800/60`
                                : 'hover:bg-slate-900/40 opacity-70'
                            }`}
                          >
                            <td className="p-2 text-center" onClick={(e) => e.stopPropagation()}>
                              <input
                                type="checkbox"
                                checked={part.selected}
                                onChange={() => togglePartition(idx)}
                                className="w-3.5 h-3.5 rounded bg-slate-900 border-slate-700 text-cyan-400 focus:ring-0 cursor-pointer"
                              />
                            </td>
                            <td className="p-2 font-bold text-white flex items-center gap-1">
                              {part.name.includes('nv') || part.name === 'persist' ? (
                                <span className="text-amber-400">⚠️ {part.name}</span>
                              ) : (
                                <span>{part.name}</span>
                              )}
                            </td>
                            <td className="p-2 text-slate-400 truncate max-w-[140px]">{part.filename}</td>
                            <td className="p-2 text-cyan-400">{part.offsetHex}</td>
                            <td className="p-2 text-slate-300">{part.sizeMB.toLocaleString()} MB</td>
                            <td className="p-2">
                              {part.selected ? (
                                <span className="text-[10px] text-emerald-400 font-bold">READY</span>
                              ) : (
                                <span className="text-[10px] text-slate-500">SKIP</span>
                              )}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  {/* Partition Footer */}
                  <div className="flex items-center justify-between text-[11px] font-mono text-slate-400 pt-1">
                    <span>Target Storage: <strong>UFS 2.2 / eMMC 5.1</strong></span>
                    <span className="text-emerald-400 font-bold">Checksum: SHA-256 Auto-Verify Active</span>
                  </div>
                </div>

                {/* Module 4: Real-Time BROM Terminal & Packet Logger */}
                <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-3.5 space-y-2`}>
                  <div className="flex items-center justify-between border-b border-slate-800 pb-2">
                    <div className="flex items-center gap-2">
                      <Terminal className="w-3.5 h-3.5 text-cyan-400" />
                      <span className="text-xs font-mono font-bold text-slate-300 uppercase">
                        Hardware Bus Terminal (libjin_usb CDC-ACM)
                      </span>
                    </div>

                    <button
                      onClick={() => setLogs([])}
                      className="text-[10px] font-mono text-slate-400 hover:text-white transition flex items-center gap-1"
                    >
                      <RotateCcw className="w-3 h-3" />
                      <span>Clear</span>
                    </button>
                  </div>

                  {/* Terminal Display Well */}
                  <div className={`p-3 ${radiusClass.card} border font-mono text-xs max-h-48 overflow-y-auto space-y-1 ${activeTheme.terminalBg}`}>
                    {logs.map((log) => (
                      <div key={log.id} className="flex items-start gap-2 text-[11px] leading-relaxed">
                        <span className="text-slate-600 shrink-0">[{log.time}]</span>
                        <span
                          className={`px-1 py-0.2 rounded text-[9px] font-black shrink-0 ${
                            log.type === 'HEX'
                              ? 'bg-amber-950 text-amber-300 border border-amber-800'
                              : log.type === 'SUCCESS'
                              ? 'bg-emerald-950 text-emerald-300 border border-emerald-800'
                              : log.type === 'WARNING'
                              ? 'bg-rose-950 text-rose-300 border border-rose-800'
                              : 'bg-sky-950 text-sky-300 border border-sky-800'
                          }`}
                        >
                          {log.type}
                        </span>
                        <span className="text-slate-300 break-all">{log.text}</span>
                      </div>
                    ))}
                    <div ref={terminalEndRef} />
                  </div>
                </div>

              </div>

            </div>

          </div>
        )}

        {/* VIEW: LICENSE & APK SECURITY GATE (SIGN IN / SIGN UP / APK SECURITY) */}
        {viewTab === 'license' && (
          <div className="max-w-2xl mx-auto space-y-4">
            {/* Header Status Card */}
            <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-4 space-y-3`}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="w-10 h-10 rounded-lg bg-cyan-500/10 border border-cyan-500/30 flex items-center justify-center text-cyan-400">
                    <ShieldCheck className="w-5 h-5" />
                  </div>
                  <div>
                    <h2 className="text-base font-bold text-white tracking-wide">NATIVE UNLOCK TOOL</h2>
                    <p className="text-[11px] font-mono text-slate-400">License Activation & Security Gateway</p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-emerald-500/15 border border-emerald-500/40 text-emerald-400">
                    APK SIG: VALID
                  </span>
                  <span className="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-cyan-500/15 border border-cyan-500/40 text-cyan-400">
                    SO ENGINE: OK
                  </span>
                </div>
              </div>

              <div className="h-px bg-slate-800" />

              <div className="flex items-center justify-between text-xs font-mono">
                <div className="flex items-center gap-2 text-slate-300">
                  <Smartphone className="w-4 h-4 text-cyan-400" />
                  <span>Redmi 9 (M2004J19G) • <span className="text-cyan-300 font-bold">HWID: MTK-8A00-6768-D9E2</span></span>
                </div>
                <button
                  onClick={() => {
                    navigator.clipboard?.writeText('MTK-8A00-6768-D9E2');
                    setGateAlarm({ type: 'success', message: 'HWID copied to clipboard: MTK-8A00-6768-D9E2' });
                  }}
                  className="text-xs font-bold text-cyan-400 hover:text-cyan-300 uppercase tracking-wider px-2 py-0.5 rounded bg-cyan-950/40 border border-cyan-800/60"
                >
                  COPY
                </button>
              </div>
            </div>

            {/* APK Security Matrix Card */}
            <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-4 space-y-3`}>
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-white text-xs font-bold font-mono">
                  <ShieldCheck className="w-4 h-4 text-cyan-400" />
                  <span>APK & NATIVE SECURITY MATRIX (Apk လုံခြုံရေးစနစ်)</span>
                </div>
                <span className="px-2 py-0.5 rounded text-[10px] font-mono font-extrabold bg-emerald-500/20 text-emerald-400 border border-emerald-500/40">
                  ARMORED
                </span>
              </div>

              <div className="grid grid-cols-2 gap-2 text-xs font-mono">
                <div className="bg-slate-950/60 border border-emerald-500/30 p-2.5 rounded">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-400">APK Signature</span>
                    <span className="text-emerald-400 font-bold">VALID</span>
                  </div>
                  <p className="text-[10px] text-slate-300 mt-1">SHA-256 Release Sign</p>
                </div>

                <div className="bg-slate-950/60 border border-emerald-500/30 p-2.5 rounded">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-400">Native Engine</span>
                    <span className="text-emerald-400 font-bold">LOCKED</span>
                  </div>
                  <p className="text-[10px] text-slate-300 mt-1">libjin_usb.so C++ OK</p>
                </div>

                <div className="bg-slate-950/60 border border-emerald-500/30 p-2.5 rounded">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-400">Device Lock</span>
                    <span className="text-emerald-400 font-bold">BOUND</span>
                  </div>
                  <p className="text-[10px] text-slate-300 mt-1">HWID: MTK-8A00-67...</p>
                </div>

                <div className="bg-slate-950/60 border border-emerald-500/30 p-2.5 rounded">
                  <div className="flex items-center justify-between text-[11px]">
                    <span className="text-slate-400">Clock Guard</span>
                    <span className="text-emerald-400 font-bold">SECURE</span>
                  </div>
                  <p className="text-[10px] text-slate-300 mt-1">Anti-Rollback Active</p>
                </div>
              </div>
            </div>

            {/* Tab Switcher: Sign Up / Activate | Sign In / Restore | Admin Debug */}
            <div className="bg-slate-950 border border-slate-800 p-1 rounded-lg flex items-center gap-1 font-mono text-xs">
              <button
                onClick={() => setGateSubTab('activate')}
                className={`flex-1 py-2 px-3 rounded text-center font-bold transition flex items-center justify-center gap-1.5 ${
                  gateSubTab === 'activate' ? 'bg-cyan-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Key className="w-3.5 h-3.5" />
                <span>Sign Up / Activate (အသစ်)</span>
              </button>

              <button
                onClick={() => setGateSubTab('restore')}
                className={`flex-1 py-2 px-3 rounded text-center font-bold transition flex items-center justify-center gap-1.5 ${
                  gateSubTab === 'restore' ? 'bg-indigo-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Lock className="w-3.5 h-3.5" />
                <span>Sign In / Restore (ပြန်လည်ရယူ)</span>
              </button>

              <button
                onClick={() => setGateSubTab('admin')}
                className={`py-2 px-3 rounded text-center font-bold transition flex items-center justify-center gap-1.5 ${
                  gateSubTab === 'admin' ? 'bg-amber-600 text-white shadow-sm' : 'text-slate-400 hover:text-white'
                }`}
              >
                <Cpu className="w-3.5 h-3.5" />
                <span>Admin Keygen</span>
              </button>
            </div>

            {/* High-Contrast Alarm / Status Notice Box */}
            {gateAlarm && (
              <div
                className={`p-3 rounded-lg border text-xs flex items-center gap-2.5 ${
                  gateAlarm.type === 'error'
                    ? 'bg-rose-950/40 border-rose-600/70 text-rose-300'
                    : gateAlarm.type === 'success'
                    ? 'bg-emerald-950/40 border-emerald-500/70 text-emerald-300'
                    : 'bg-amber-950/40 border-amber-500/70 text-amber-300'
                }`}
              >
                {gateAlarm.type === 'error' ? (
                  <AlertTriangle className="w-4 h-4 shrink-0 text-rose-400" />
                ) : gateAlarm.type === 'success' ? (
                  <CheckCircle2 className="w-4 h-4 shrink-0 text-emerald-400" />
                ) : (
                  <Info className="w-4 h-4 shrink-0 text-amber-400" />
                )}
                <div className="flex-1 font-sans">
                  <span className="font-bold uppercase tracking-wider block text-[10px] font-mono">
                    {gateAlarm.type === 'error' ? 'ALARM / SECURITY ERROR' : gateAlarm.type === 'success' ? 'SYSTEM SUCCESS' : 'SYSTEM NOTICE'}
                  </span>
                  <span>{gateAlarm.message}</span>
                </div>
              </div>
            )}

            {/* TAB 1: SIGN UP & ACTIVATE */}
            {gateSubTab === 'activate' && (
              <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-4 space-y-4`}>
                {/* 1. Google Account Direct (ဒါရိုက်) */}
                <div className="bg-slate-950/70 border border-slate-800 p-3 rounded-lg space-y-2.5">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <div className="w-6 h-6 rounded bg-white flex items-center justify-center font-black text-sm text-[#4285F4]">
                        G
                      </div>
                      <div>
                        <div className="text-xs font-bold text-white">Google Account Direct (ဒါရိုက်)</div>
                        <div className="text-[10px] text-slate-400">
                          {googleUser ? `Linked: ${googleUser.email}` : 'One-tap direct identity sign-in'}
                        </div>
                      </div>
                    </div>
                    {googleUser && (
                      <button
                        onClick={() => {
                          setGoogleUser(null);
                          setGateAlarm({ type: 'warning', message: 'Disconnected from Google Account.' });
                        }}
                        className="text-[10px] font-mono font-bold text-rose-400 hover:text-rose-300 uppercase px-2 py-0.5 rounded bg-rose-950/40 border border-rose-800"
                      >
                        Disconnect
                      </button>
                    )}
                  </div>

                  {!googleUser ? (
                    <button
                      onClick={() => {
                        const mock = { email: 'burmese.tech@gmail.com', name: 'Burmese GSM Tech' };
                        setGoogleUser(mock);
                        setRegEmail(mock.email);
                        setGateAlarm({ type: 'success', message: `Google Direct Sign-In Connected: ${mock.email}` });
                      }}
                      className="w-full h-10 rounded bg-[#1E293B] hover:bg-[#334155] border border-[#4285F4] text-white text-xs font-bold flex items-center justify-center gap-2 transition"
                    >
                      <div className="w-4 h-4 rounded bg-white flex items-center justify-center font-black text-[10px] text-[#4285F4]">
                        G
                      </div>
                      <span>Continue with Google (Google အကောင့်ဖြင့် ဒါရိုက်ဝင်မည်)</span>
                    </button>
                  ) : (
                    <div className="text-xs font-mono text-emerald-400 bg-emerald-950/30 border border-emerald-500/30 px-3 py-1.5 rounded flex items-center gap-2">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>Ready for 1-Tap Google Activation</span>
                    </div>
                  )}
                </div>

                {/* Divider */}
                <div className="flex items-center gap-2 font-mono text-[10px] text-slate-500 uppercase">
                  <div className="flex-1 h-px bg-slate-800" />
                  <span>OR REGISTER WITH EMAIL & PASSWORD (သီးသန့်)</span>
                  <div className="flex-1 h-px bg-slate-800" />
                </div>

                {/* Dedicated Email & Password Form */}
                <div className="space-y-3 font-mono text-xs">
                  <div>
                    <label className="block text-[11px] text-slate-400 font-bold mb-1">Username</label>
                    <input
                      type="text"
                      value={regUsername}
                      onChange={(e) => setRegUsername(e.target.value)}
                      placeholder="e.g. GSM_Hero"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-white focus:border-cyan-400 outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-[11px] text-slate-400 font-bold mb-1">Email Address (သီးသန့်)</label>
                    <input
                      type="email"
                      value={regEmail}
                      onChange={(e) => setRegEmail(e.target.value)}
                      placeholder="engineer@gsmtool.com"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-white focus:border-cyan-400 outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-[11px] text-slate-400 font-bold mb-1">Password (min 6 characters)</label>
                    <div className="relative">
                      <input
                        type={regShowPassword ? 'text' : 'password'}
                        value={regPassword}
                        onChange={(e) => setRegPassword(e.target.value)}
                        placeholder="••••••••"
                        className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-white focus:border-cyan-400 outline-none pr-16"
                      />
                      <button
                        type="button"
                        onClick={() => setRegShowPassword(!regShowPassword)}
                        className="absolute right-2 top-2 text-[10px] font-bold text-slate-400 hover:text-white uppercase"
                      >
                        {regShowPassword ? 'HIDE' : 'SHOW'}
                      </button>
                    </div>
                  </div>

                  <div>
                    <label className="block text-[11px] text-slate-400 font-bold mb-1">License Key (လိုင်စင်ကီး)</label>
                    <input
                      type="text"
                      value={regLicenseKey}
                      onChange={(e) => setRegLicenseKey(e.target.value.toUpperCase())}
                      placeholder="MTK-0030-XXXX-XXXX-XXXXXXXX"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-cyan-400 font-bold focus:border-cyan-400 outline-none"
                    />
                  </div>

                  <button
                    onClick={() => {
                      if (!regEmail.includes('@')) {
                        setGateAlarm({ type: 'error', message: 'Please enter a valid email address.' });
                        return;
                      }
                      if (regPassword.length < 6) {
                        setGateAlarm({ type: 'error', message: 'Password must be at least 6 characters.' });
                        return;
                      }
                      if (!regLicenseKey.trim()) {
                        setGateAlarm({ type: 'error', message: 'Please provide a valid license key.' });
                        return;
                      }
                      setActiveSession({
                        unlocked: true,
                        user: regEmail,
                        key: regLicenseKey,
                        type: regLicenseKey.includes('LIFE') ? 'Lifetime Subscription' : '30-Day Pro Subscription',
                        remainingDays: 30,
                        hwid: 'MTK-8A00-6768-D9E2'
                      });
                      setGateAlarm({
                        type: 'success',
                        message: `License Activated Successfully! Account registered: ${regEmail}. HWID bound to MTK-8A00-6768-D9E2.`
                      });
                    }}
                    className="w-full h-11 rounded bg-cyan-600 hover:bg-cyan-500 font-bold text-white text-xs flex items-center justify-center gap-2 shadow-lg transition"
                  >
                    <Key className="w-4 h-4" />
                    <span>ACTIVATE KEY & REGISTER (လိုင်စင်ထည့်သွင်းမည်)</span>
                  </button>

                  <button
                    onClick={() => {
                      setActiveSession({
                        unlocked: true,
                        user: 'offline_user@local',
                        key: regLicenseKey || 'MTK-OFFLINE-LOCAL',
                        type: 'Offline Bound Key (No GMS)',
                        remainingDays: 30,
                        hwid: 'MTK-8A00-6768-D9E2'
                      });
                      setGateAlarm({
                        type: 'success',
                        message: 'Offline HWID Local Activation Success! Ready for China ROM / No Google Play devices.'
                      });
                    }}
                    className="w-full h-9 rounded bg-amber-950/40 hover:bg-amber-900/60 border border-amber-600/70 text-amber-300 font-bold text-xs flex items-center justify-center gap-2 transition"
                  >
                    <span>OFFLINE ACTIVATE (NO GMS / CHINA ROM)</span>
                  </button>
                </div>
              </div>
            )}

            {/* TAB 2: SIGN IN & RESTORE */}
            {gateSubTab === 'restore' && (
              <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-4 space-y-4`}>
                {/* Google 1-Tap Restore */}
                {googleUser ? (
                  <button
                    onClick={() => {
                      setActiveSession({
                        unlocked: true,
                        user: googleUser.email,
                        key: 'MTK-GOOGLE-RESTORED-SUB',
                        type: 'Cloud Restored (Google)',
                        remainingDays: 28,
                        hwid: 'MTK-8A00-6768-D9E2'
                      });
                      setGateAlarm({
                        type: 'success',
                        message: `License Restored from Google Cloud Account: ${googleUser.email}!`
                      });
                    }}
                    className="w-full h-11 rounded bg-emerald-600 hover:bg-emerald-500 font-bold text-white text-xs flex items-center justify-center gap-2 shadow-lg transition"
                  >
                    <CheckCircle2 className="w-4 h-4" />
                    <span>RESTORE WITH GOOGLE ({googleUser.email})</span>
                  </button>
                ) : (
                  <button
                    onClick={() => {
                      const mock = { email: 'burmese.tech@gmail.com', name: 'Burmese GSM Tech' };
                      setGoogleUser(mock);
                      setLoginEmail(mock.email);
                      setActiveSession({
                        unlocked: true,
                        user: mock.email,
                        key: 'MTK-GOOGLE-RESTORED-SUB',
                        type: 'Cloud Restored (Google)',
                        remainingDays: 28,
                        hwid: 'MTK-8A00-6768-D9E2'
                      });
                      setGateAlarm({
                        type: 'success',
                        message: `Direct Google Login Verified! License automatically restored for ${mock.email}.`
                      });
                    }}
                    className="w-full h-10 rounded bg-[#1E293B] hover:bg-[#334155] border border-[#4285F4] text-white text-xs font-bold flex items-center justify-center gap-2 transition"
                  >
                    <div className="w-4 h-4 rounded bg-white flex items-center justify-center font-black text-[10px] text-[#4285F4]">
                      G
                    </div>
                    <span>Continue with Google (Google အကောင့်ဖြင့် တိုက်ရိုက်ပြန်လည်ရယူမည်)</span>
                  </button>
                )}

                {/* Divider */}
                <div className="flex items-center gap-2 font-mono text-[10px] text-slate-500 uppercase">
                  <div className="flex-1 h-px bg-slate-800" />
                  <span>OR RESTORE WITH EMAIL & PASSWORD (သီးသန့်)</span>
                  <div className="flex-1 h-px bg-slate-800" />
                </div>

                {/* Dedicated Email & Password Restore Form */}
                <div className="space-y-3 font-mono text-xs">
                  <div>
                    <label className="block text-[11px] text-slate-400 font-bold mb-1">Registered Email (မှတ်ပုံတင်ထားသော အီးမေးလ်)</label>
                    <input
                      type="email"
                      value={loginEmail}
                      onChange={(e) => setLoginEmail(e.target.value)}
                      placeholder="engineer@gsmtool.com"
                      className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-white focus:border-indigo-400 outline-none"
                    />
                  </div>

                  <div>
                    <label className="block text-[11px] text-slate-400 font-bold mb-1">Password</label>
                    <div className="relative">
                      <input
                        type={loginShowPassword ? 'text' : 'password'}
                        value={loginPassword}
                        onChange={(e) => setLoginPassword(e.target.value)}
                        placeholder="••••••••"
                        className="w-full bg-slate-950 border border-slate-800 rounded px-3 py-2 text-white focus:border-indigo-400 outline-none pr-16"
                      />
                      <button
                        type="button"
                        onClick={() => setLoginShowPassword(!loginShowPassword)}
                        className="absolute right-2 top-2 text-[10px] font-bold text-slate-400 hover:text-white uppercase"
                      >
                        {loginShowPassword ? 'HIDE' : 'SHOW'}
                      </button>
                    </div>
                  </div>

                  <button
                    onClick={() => {
                      if (!loginEmail.includes('@') || !loginPassword.trim()) {
                        setGateAlarm({ type: 'error', message: 'Please enter your registered email and password.' });
                        return;
                      }
                      setActiveSession({
                        unlocked: true,
                        user: loginEmail,
                        key: 'MTK-RESTORED-ACCT-KEY',
                        type: 'Restored Active Account',
                        remainingDays: 25,
                        hwid: 'MTK-8A00-6768-D9E2'
                      });
                      setGateAlarm({
                        type: 'success',
                        message: `Account & License Restored! Welcome back: ${loginEmail}`
                      });
                    }}
                    className="w-full h-11 rounded bg-indigo-600 hover:bg-indigo-500 font-bold text-white text-xs flex items-center justify-center gap-2 shadow-lg transition"
                  >
                    <Lock className="w-4 h-4" />
                    <span>SIGN IN & RESTORE LICENSE (လိုင်စင်ပြန်လည်ရယူမည်)</span>
                  </button>
                </div>
              </div>
            )}

            {/* TAB 3: ADMIN KEYGEN (DEBUG) */}
            {gateSubTab === 'admin' && (
              <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-4 space-y-3 font-mono text-xs`}>
                <div className="flex items-center justify-between">
                  <div className="font-bold text-amber-400 flex items-center gap-2">
                    <Cpu className="w-4 h-4" />
                    <span>Master Keygen Panel (ARX 4-Lane Cryptographic Engine)</span>
                  </div>
                  <span className="text-[10px] text-slate-400">DEBUG ONLY</span>
                </div>

                <p className="text-[11px] text-slate-300">
                  Select a generated key below to auto-fill into the Activation form:
                </p>

                <div className="space-y-2">
                  {[
                    { code: 'MTK-0030-BROM-LIVE-88991122', type: '30 Days Standard' },
                    { code: 'MTK-0365-VIP4-KAMA-19283746', type: '365 Days VIP' },
                    { code: 'MTK-0000-LIFE-ARX4-55443322', type: 'Lifetime Unlimited' }
                  ].map((item, idx) => (
                    <div key={idx} className="bg-slate-950 border border-slate-800 p-2.5 rounded flex items-center justify-between">
                      <div>
                        <div className="font-bold text-cyan-400">{item.code}</div>
                        <div className="text-[10px] text-slate-500">{item.type}</div>
                      </div>
                      <button
                        onClick={() => {
                          setRegLicenseKey(item.code);
                          setGateSubTab('activate');
                          setGateAlarm({ type: 'success', message: `Loaded key ${item.code} into Activation form.` });
                        }}
                        className="px-2.5 py-1 rounded bg-cyan-950 border border-cyan-800 text-cyan-300 hover:bg-cyan-900 font-bold text-[10px]"
                      >
                        USE THIS KEY
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Direct Security Warning Alert Card (No Tutorial Clutter) */}
            <div className="bg-rose-950/20 border border-rose-600/50 rounded-lg p-4 space-y-3 text-xs">
              <div className="flex items-center gap-2 text-rose-400 font-bold font-mono">
                <AlertTriangle className="w-4 h-4 text-rose-500" />
                <span className="tracking-wide">CRITICAL SECURITY WARNING (အရေးပေါ် လုံခြုံရေး သတိပေးချက်)</span>
              </div>

              <div className="h-px bg-rose-600/30" />

              <div className="space-y-2.5 font-mono">
                {/* Warning 1: HWID Lock */}
                <div className="flex items-start gap-2.5">
                  <span className="px-1.5 py-0.5 rounded text-[9px] font-black bg-rose-500/20 text-rose-300 border border-rose-500/40 shrink-0 mt-0.5">
                    HWID LOCK
                  </span>
                  <div>
                    <div className="text-white font-bold text-xs">
                      Single-Device HWID Lock: Key bound strictly to (MTK-8A00-6768-D9E2).
                    </div>
                    <div className="text-[11px] text-slate-300 mt-0.5">
                      လိုင်စင်ကီးကို ဤဖုန်း၏ HWID နှင့်သာ အသေချိတ်ထားပြီး အခြားဖုန်းသို့ လွှဲပြောင်းမျှဝေခြင်းကို တားမြစ်ထားသည်။
                    </div>
                  </div>
                </div>

                {/* Warning 2: Tamper Ban */}
                <div className="flex items-start gap-2.5">
                  <span className="px-1.5 py-0.5 rounded text-[9px] font-black bg-rose-500/20 text-rose-300 border border-rose-500/40 shrink-0 mt-0.5">
                    TAMPER BAN
                  </span>
                  <div>
                    <div className="text-white font-bold text-xs">
                      Anti-Tamper: Modifying APK, resigning signatures, or clock rollback triggers permanent HWID ban.
                    </div>
                    <div className="text-[11px] text-slate-300 mt-0.5">
                      APK အား ပြင်ဆင်ခြင်း၊ Signature ပြောင်းလဲခြင်း သို့မဟုတ် နာရီ နောက်ပြန်လှည့်ပါက အကောင့်နှင့် HWID အပြီးတိုင် Ban ပါမည်။
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* VIEW 2: OLD MATERIAL VS NEW GSM BOX COMPARISON */}
        {viewTab === 'comparison' && (
          <div className="space-y-6">
            <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-6 space-y-4`}>
              <div>
                <span className="text-xs font-mono font-bold px-2.5 py-1 rounded bg-cyan-950 text-cyan-400 border border-cyan-800">
                  DESIGN SYSTEM UPGRADE ANALYSIS
                </span>
                <h2 className="text-xl font-bold text-white mt-2">
                  လက်ရှိ Jetpack Compose UI (Material 3) နှင့် Professional GSM Box Design နှိုင်းယှဉ်ချက်
                </h2>
                <p className="text-xs text-slate-400 mt-1">
                  သင် စိတ်တိုင်းမကျဖြစ်နေသော အချက် ၄ ချက် (Color Design, Card Form, Button Form, Sizes) ကို အတိအကျ နှိုင်းယှဉ်ပြသထားပါသည်။
                </p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6 pt-2">
                
                {/* LEFT: Old Material 3 (What user hated) */}
                <div className="bg-[#121620] border-2 border-dashed border-rose-500/40 rounded-2xl p-5 space-y-4">
                  <div className="flex items-center justify-between pb-3 border-b border-rose-500/20">
                    <span className="text-sm font-bold text-rose-400 flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-rose-500" />
                      ❌ ယခင် မူရင်း Jetpack Compose (Material 3)
                    </span>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-rose-950 text-rose-300">
                      Disliked by User
                    </span>
                  </div>

                  <div className="space-y-3 text-xs text-slate-300">
                    <div className="p-3 bg-slate-900 rounded-xl space-y-1">
                      <div className="font-bold text-rose-300">၁။ အရောင်ဒီဇိုင်း (Color Design)</div>
                      <p className="text-slate-400">
                        Generic SaaS Light/Dark Blue (`#0EA5E9`, `#6366F1`) ဖြစ်နေပြီး Web Dashboard သို့မဟုတ် Consumer App ပုံစံ ပေါက်နေခြင်း။
                      </p>
                    </div>

                    <div className="p-3 bg-slate-900 rounded-xl space-y-1">
                      <div className="font-bold text-rose-300">၂။ Card ပုံစံ (Card Forms)</div>
                      <p className="text-slate-400">
                        `RoundedCornerShape(10.dp / 14.dp)` ဖြင့် ကတ်ပြားများ အဝိုင်းလွန်ပြီး ဖောင်းဖောင်းအိအိ ဖြစ်နေခြင်း။
                      </p>
                    </div>

                    <div className="p-3 bg-slate-900 rounded-xl space-y-1">
                      <div className="font-bold text-rose-300">၃။ ခလုတ်ပုံစံ (Button Forms)</div>
                      <p className="text-slate-400">
                        Material3 Button များသည် ပျော့ပြောင်းသော Pill Shape ဖြင့် ပြားကပ်နေပြီး Hardware Key ခံစားချက် မရှိခြင်း။
                      </p>
                    </div>

                    <div className="p-3 bg-slate-900 rounded-xl space-y-1">
                      <div className="font-bold text-rose-300">၄။ အရွယ်အစား (Sizes & Spacing)</div>
                      <p className="text-slate-400">
                        Button Height 48px~56px၊ Padding 12px~16px ဖြင့် အရွယ်အစား ပွလွန်းသဖြင့် Partition Table နှင့် Log နေရာ မဆံ့ခြင်း။
                      </p>
                    </div>
                  </div>
                </div>

                {/* RIGHT: New Pro GSM Box (The solution) */}
                <div className="bg-[#090D15] border-2 border-emerald-500/50 rounded-md p-5 space-y-4 shadow-lg shadow-emerald-500/10">
                  <div className="flex items-center justify-between pb-3 border-b border-emerald-500/20">
                    <span className="text-sm font-bold text-emerald-400 flex items-center gap-2">
                      <span className="w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
                      ✅ အသစ် ပြင်ဆင်ထားသော Pro GSM Box Design
                    </span>
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded bg-emerald-950 text-emerald-300 font-bold">
                      UnlockTool Standard
                    </span>
                  </div>

                  <div className="space-y-3 text-xs text-slate-300">
                    <div className="p-3 bg-[#0E131E] rounded-[3px] border border-emerald-500/30 space-y-1">
                      <div className="font-bold text-emerald-300">၁။ အရောင်ဒီဇိုင်း (Color Design)</div>
                      <p className="text-slate-400">
                        Deepest Carbon Chassis (`#07090E`) + High-Voltage Laser Cyan (`#00E5FF`) + 1px Hairline Steel Borders (`#1C2536`)။
                      </p>
                    </div>

                    <div className="p-3 bg-[#0E131E] rounded-[3px] border border-emerald-500/30 space-y-1">
                      <div className="font-bold text-emerald-300">၂။ Card ပုံစံ (Card Forms)</div>
                      <p className="text-slate-400">
                        Sharp 2px-3px Hardware Bevel ဖြင့် တိကျပြတ်သားသော Hardware Console Panel ပုံစံ။
                      </p>
                    </div>

                    <div className="p-3 bg-[#0E131E] rounded-[3px] border border-emerald-500/30 space-y-1">
                      <div className="font-bold text-emerald-300">၃။ ခလုတ်ပုံစံ (Button Forms)</div>
                      <p className="text-slate-400">
                        Linear Metallic Gradient (#182233 to #0E1522) + Top Hairline Highlight ဖြင့် ထိတွေ့ရလွယ်သော Tactile Keys။
                      </p>
                    </div>

                    <div className="p-3 bg-[#0E131E] rounded-[3px] border border-emerald-500/30 space-y-1">
                      <div className="font-bold text-emerald-300">၄။ အရွယ်အစား (Sizes & Spacing)</div>
                      <p className="text-slate-400">
                        Compact Height 30px-34px၊ Spacing 3px-4px ဖြင့် High-Density Ergonomics ရရှိပြီး မျက်နှာပြင်နေရာ အပြည့်အဝ အသုံးချနိုင်ခြင်း။
                      </p>
                    </div>
                  </div>
                </div>

              </div>
            </div>
          </div>
        )}

        {/* VIEW 3: JETPACK COMPOSE SOURCE CODE EXPORT */}
        {viewTab === 'code' && (
          <div className="space-y-6">
            <div className={`${activeTheme.surface} ${borderClass} ${radiusClass.card} p-6 space-y-4`}>
              <div className="flex items-center justify-between">
                <div>
                  <h3 className="text-base font-bold text-white flex items-center gap-2">
                    <FileCode className="w-4 h-4 text-cyan-400" />
                    <span>Android Jetpack Compose Code Implementation</span>
                  </h3>
                  <p className="text-xs text-slate-400">
                    သင်၏ `app/src/main/java/com/example/ui/screens/UnlockToolFlashScreen.kt` တွင် ဤ Sharp Bevel & Compact Button Component များကို အစားထိုးနိုင်ပါသည်
                  </p>
                </div>

                <button
                  onClick={() => copyCode('composeBtn', `// === PRO HARDWARE TACTILE GSM BUTTON (Jetpack Compose) ===
@Composable
fun GsmHardwareButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDanger: Boolean = false
) {
    val palette = LocalAppPalette.current
    val bgBrush = when {
        isPrimary -> Brush.horizontalGradient(listOf(Color(0xFF00A3FF), Color(0xFF00E5FF)))
        isDanger -> Brush.horizontalGradient(listOf(Color(0xFFFF1744), Color(0xFFD50000)))
        else -> Brush.verticalGradient(listOf(Color(0xFF182233), Color(0xFF0E1522)))
    }
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(3.dp), // Sharp 3px Bevel
        border = BorderStroke(1.dp, if (isPrimary || isDanger) Color.Transparent else Color(0xFF26354D)),
        modifier = modifier.height(32.dp) // Compact 32px height
    ) {
        Box(
            modifier = Modifier
                .background(bgBrush)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPrimary) Color(0xFF07090E) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isPrimary) Color(0xFF07090E) else Color.White
                )
            }
        }
    }
}`)}
                  className="flex items-center gap-1.5 text-xs bg-slate-800 hover:bg-slate-700 px-3 py-1.5 rounded text-white font-mono transition"
                >
                  {copiedKey === 'composeBtn' ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedKey === 'composeBtn' ? 'Copied!' : 'Copy Compose Component'}</span>
                </button>
              </div>

              <pre className="bg-[#05070B] p-4 rounded border border-slate-800 text-xs font-mono text-cyan-300 overflow-x-auto max-h-96">
{`// === PRO HARDWARE TACTILE GSM BUTTON (Jetpack Compose) ===
@Composable
fun GsmHardwareButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPrimary: Boolean = false,
    isDanger: Boolean = false
) {
    val palette = LocalAppPalette.current
    val bgBrush = when {
        isPrimary -> Brush.horizontalGradient(listOf(Color(0xFF00A3FF), Color(0xFF00E5FF)))
        isDanger -> Brush.horizontalGradient(listOf(Color(0xFFFF1744), Color(0xFFD50000)))
        else -> Brush.verticalGradient(listOf(Color(0xFF182233), Color(0xFF0E1522)))
    }
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(3.dp), // Sharp 3px Bevel
        border = BorderStroke(1.dp, if (isPrimary || isDanger) Color.Transparent else Color(0xFF26354D)),
        modifier = modifier.height(32.dp) // Compact 32px height
    ) {
        Box(
            modifier = Modifier
                .background(bgBrush)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isPrimary) Color(0xFF07090E) else Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Text(
                    text = text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if (isPrimary) Color(0xFF07090E) else Color.White
                )
            }
        }
    }
}`}
              </pre>
            </div>
          </div>
        )}

      </main>

      {/* FOOTER */}
      <footer className="border-t border-slate-800/80 bg-[#07090E] px-4 py-2.5 text-xs text-slate-500 font-mono">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400" />
            <span>Active Preset: {activeTheme.name} • {radiusClass.tag} • {operatingMode} Mode</span>
          </div>
          <div>Mas Zar Ni Crack V4 Engine</div>
        </div>
      </footer>

      {/* MODAL 1: STARTUP ONLINE / OFFLINE MODE CHOOSER */}
      {showStartupModeModal && (
        <div className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4">
          <div className="w-full max-w-md bg-[#0E131C] border border-slate-700 rounded-md p-4 space-y-3 shadow-2xl">
            <div className="flex items-start justify-between">
              <div>
                <h3 className="text-sm font-bold text-white">Database & Loader Operating Mode</h3>
                <p className="text-[11px] font-mono text-cyan-400 tabular-nums">
                  model_map.json v105 · {MODEL_MAP_ENTRIES.length} verified Firehose loaders
                </p>
              </div>
              <button onClick={() => setShowStartupModeModal(false)} className="text-slate-400 hover:text-white">
                <X className="w-4 h-4" />
              </button>
            </div>

            <button
              onClick={() => switchAppOperatingMode('ONLINE')}
              className={`w-full text-left p-3 rounded border transition space-y-1 ${
                operatingMode === 'ONLINE'
                  ? 'bg-cyan-950/40 border-cyan-400'
                  : 'bg-slate-950 border-slate-800 hover:border-slate-700'
              }`}
            >
              <div className="flex items-center gap-2 text-xs font-bold text-cyan-400">
                <Cloud className="w-4 h-4" />
                <span>ONLINE MODE (Live Stream)</span>
              </div>
              <p className="text-[11px] text-slate-300 leading-relaxed">
                Storage တွင် File များ Download ဆွဲမထားပါ။ ဖုန်းချိတ်ဆက်သည့်အချိန်မှသာ model_map.json မှတဆင့် လိုအပ်သော Firehose Loader ကို Live Stream ဖြင့် RAM ထဲသို့ တိုက်ရိုက်သွင်းပေးမည်။
              </p>
            </button>

            <button
              onClick={() => switchAppOperatingMode('OFFLINE', true)}
              className={`w-full text-left p-3 rounded border transition space-y-1 ${
                operatingMode === 'OFFLINE'
                  ? 'bg-emerald-950/40 border-emerald-400'
                  : 'bg-slate-950 border-slate-800 hover:border-slate-700'
              }`}
            >
              <div className="flex items-center gap-2 text-xs font-bold text-emerald-400">
                <HardDrive className="w-4 h-4" />
                <span>OFFLINE MODE (Download & Sync All)</span>
              </div>
              <p className="text-[11px] text-slate-300 leading-relaxed">
                model_map.json နှင့် Loader Binary များအားလုံးကို Internal Storage (/NativeUnlockTool/loaders/) ထဲသို့ Save ထားမည်။ အင်တာနက်မရှိချိန် 100% အလုပ်လုပ်နိုင်ပြီး Online ပြန်ရချိန် Auto Sync လုပ်နိုင်သည်။
              </p>
            </button>

            <div className="flex items-center justify-end gap-2 pt-1 font-mono text-xs">
              <button
                onClick={() => switchAppOperatingMode('OFFLINE', false)}
                className="px-3 py-1.5 rounded border border-slate-700 text-slate-300 hover:text-white"
              >
                Offline Only
              </button>
              <button
                onClick={() => switchAppOperatingMode('OFFLINE', true)}
                className="px-3 py-1.5 rounded bg-emerald-600 hover:bg-emerald-500 text-white font-bold"
              >
                Offline + Sync Now
              </button>
            </div>
          </div>
        </div>
      )}

      {/* MODAL 2: QUALCOMM bkerler/Loaders REPOSITORY BROWSER & SYNC */}
      {showLoaderCatalogModal && (
        <div className="fixed inset-0 z-50 bg-black/75 flex items-center justify-center p-4">
          <div className="w-full max-w-2xl bg-[#0E131C] border border-slate-700 rounded-md p-4 space-y-3 shadow-2xl max-h-[90vh] flex flex-col">
            <div className="flex items-center justify-between gap-2 border-b border-slate-800 pb-2">
              <div>
                <h3 className="text-sm font-bold text-white">Qualcomm Loader Catalog (bkerler/Loaders)</h3>
                <p className="text-[11px] font-mono text-slate-400 tabular-nums">
                  model_map.json v105 · {MODEL_MAP_ENTRIES.length} entries · Synced: {syncedIds.length}/{MODEL_MAP_ENTRIES.length}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  onClick={() => switchAppOperatingMode(operatingMode === 'ONLINE' ? 'OFFLINE' : 'ONLINE')}
                  className={`px-2.5 py-1 rounded text-[11px] font-mono font-bold border ${
                    operatingMode === 'ONLINE'
                      ? 'bg-cyan-950/60 border-cyan-400 text-cyan-300'
                      : 'bg-emerald-950/60 border-emerald-400 text-emerald-300'
                  }`}
                >
                  {operatingMode}
                </button>
                <button onClick={() => setShowLoaderCatalogModal(false)} className="text-slate-400 hover:text-white">
                  <X className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Repo Source Endpoint Switcher (GitHub Raw -> Private Server) */}
            <div className="bg-slate-950 border border-slate-800 rounded p-2.5 space-y-2 font-mono text-xs">
              <div className="flex flex-wrap items-center gap-2">
                <input
                  type="text"
                  value={repoUrlInput}
                  onChange={e => setRepoUrlInput(e.target.value)}
                  className="flex-1 min-w-[200px] bg-[#07090E] border border-slate-800 rounded px-2.5 py-1 text-[11px] text-slate-200 focus:border-cyan-400 outline-none"
                  placeholder="https://raw.githubusercontent.com/bkerler/Loaders/master/ or Private Backend URL"
                />
                <button
                  onClick={() => {
                    setRepoUrl(repoUrlInput);
                    appendLog('INFO', `[REPO] Repository base URL updated -> ${repoUrlInput}`);
                  }}
                  className="px-2.5 py-1 rounded bg-slate-800 hover:bg-slate-700 text-cyan-300 text-[11px] font-bold"
                >
                  Apply Endpoint
                </button>
                {isSyncingLoaders ? (
                  <button
                    onClick={cancelOfflineLoaderSync}
                    className="px-2.5 py-1 rounded bg-rose-700 hover:bg-rose-600 text-white text-[11px] font-bold"
                  >
                    Cancel ({syncPercent}%)
                  </button>
                ) : (
                  <button
                    onClick={startOfflineLoaderSync}
                    className="px-2.5 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-white text-[11px] font-bold flex items-center gap-1"
                  >
                    <RefreshCw className="w-3 h-3" />
                    <span>Sync Offline ({lastSyncTime})</span>
                  </button>
                )}
              </div>

              {isSyncingLoaders && (
                <div className="space-y-1">
                  <div className="flex justify-between text-[10px] text-emerald-400 tabular-nums">
                    <span className="truncate">{syncCurrentFile}</span>
                    <span>{syncPercent}%</span>
                  </div>
                  <div className="w-full h-1.5 bg-slate-800 rounded overflow-hidden">
                    <div className="h-full bg-emerald-400 transition-all" style={{ width: `${syncPercent}%` }} />
                  </div>
                </div>
              )}
            </div>

            {/* Search & Brand Filter */}
            <div className="flex flex-wrap items-center gap-2 font-mono text-xs">
              <div className="relative flex-1 min-w-[180px]">
                <Search className="w-3.5 h-3.5 text-slate-400 absolute left-2.5 top-2" />
                <input
                  type="text"
                  value={loaderSearch}
                  onChange={e => setLoaderSearch(e.target.value)}
                  placeholder="Search brand, SoC, MSM HWID (0x000E10E1)..."
                  className="w-full bg-slate-950 border border-slate-800 rounded pl-8 pr-3 py-1.5 text-[11px] text-white focus:border-cyan-400 outline-none"
                />
              </div>
              <div className="flex flex-wrap items-center gap-1">
                {['ALL', 'Xiaomi', 'Poco', 'Oppo', 'OnePlus', 'Samsung', 'Vivo'].map(b => (
                  <button
                    key={b}
                    onClick={() => setLoaderBrandFilter(b)}
                    className={`px-2 py-1 rounded text-[10px] font-bold border ${
                      loaderBrandFilter === b
                        ? 'bg-cyan-950 text-cyan-300 border-cyan-500'
                        : 'bg-slate-950 text-slate-400 border-slate-800 hover:text-white'
                    }`}
                  >
                    {b}
                  </button>
                ))}
              </div>
            </div>

            {/* Catalog Entries */}
            <div className="flex-1 overflow-y-auto space-y-1.5 pr-1 font-mono text-xs">
              {filteredLoaderEntries.map(entry => {
                const isSelected = matchedQcLoader?.id === entry.id;
                const isLocal = syncedIds.includes(entry.id);
                return (
                  <button
                    key={entry.id}
                    onClick={() => {
                      setMatchedQcLoader(entry);
                      setPlatformMode('QC');
                      setShowLoaderCatalogModal(false);
                      appendLog(
                        'INFO',
                        `[QC CATALOG] Selected ${entry.brand} ${entry.model} (${entry.soc}) -> ${entry.file_name} [HWID ${entry.msm_hwid}]`
                      );
                    }}
                    className={`w-full text-left p-2.5 rounded border transition flex items-center justify-between gap-3 ${
                      isSelected
                        ? 'bg-emerald-950/30 border-emerald-500'
                        : 'bg-slate-950 border-slate-800 hover:border-slate-700'
                    }`}
                  >
                    <div className="min-w-0">
                      <div className="text-white font-bold text-[11px] truncate">
                        {entry.brand} · {entry.model}
                      </div>
                      <div className="text-[10px] text-slate-400 tabular-nums truncate">
                        {entry.soc} · HWID {entry.msm_hwid} · PK_HASH {entry.pk_hash}
                      </div>
                      <div className="text-[10px] text-cyan-400 truncate">{entry.relative_path}</div>
                    </div>
                    <div className="shrink-0 text-right tabular-nums">
                      <div className="text-[10px] font-bold text-slate-300">{entry.storage_type}</div>
                      <div className={`text-[10px] ${isLocal ? 'text-emerald-400' : 'text-slate-500'}`}>
                        {isLocal ? 'LOCAL CACHED' : `${(entry.file_size / 1024).toFixed(0)} KB REMOTE`}
                      </div>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
