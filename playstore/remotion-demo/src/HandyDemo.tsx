import React from 'react';
import {
  AbsoluteFill,
  Audio,
  Img,
  OffthreadVideo,
  Sequence,
  interpolate,
  spring,
  staticFile,
  useCurrentFrame,
  useVideoConfig,
} from 'remotion';

// --------- timing ---------
export const DEMO_FPS = 30;
// tune these once the raw recording is in
const INTRO_FRAMES = 60; // 2s
const BODY_FRAMES = Math.round(58.5 * DEMO_FPS); // 58.5s — matches trimmed raw
const OUTRO_FRAMES = 90; // 3s
export const DEMO_DURATION_FRAMES = INTRO_FRAMES + BODY_FRAMES + OUTRO_FRAMES;

// --------- canvas ---------
// vertical for Twitter / mobile. Phone recording is 720x1620 → scaled up.
export const DEMO_WIDTH = 1080;
export const DEMO_HEIGHT = 1920;

// --------- colors (match Handy AI) ---------
const INK = '#111111';
const CREAM = '#EDE6D6';
const CREAM_DIM = 'rgba(237,230,214,0.65)';

// --------- typography ---------
const DISPLAY_FONT = '"Fraunces", "Playfair Display", Georgia, serif';
const BODY_FONT = '"Instrument Sans", "Inter", system-ui, sans-serif';

// --------- components ---------

const Backdrop: React.FC = () => (
  <AbsoluteFill style={{background: INK}}>
    {/* subtle cream vignette */}
    <AbsoluteFill
      style={{
        background:
          'radial-gradient(ellipse at center, rgba(237,230,214,0.05) 0%, rgba(17,17,17,1) 70%)',
      }}
    />
  </AbsoluteFill>
);

const Intro: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const appear = spring({
    frame,
    fps,
    config: {damping: 200, stiffness: 100, mass: 0.6},
  });
  const y = interpolate(appear, [0, 1], [24, 0]);
  const opacity = interpolate(appear, [0, 1], [0, 1]);

  return (
    <AbsoluteFill
      style={{
        alignItems: 'center',
        justifyContent: 'center',
        color: CREAM,
        textAlign: 'center',
        padding: 80,
      }}
    >
      <div
        style={{
          transform: `translateY(${y}px)`,
          opacity,
        }}
      >
        <div
          style={{
            fontFamily: DISPLAY_FONT,
            fontSize: 96,
            lineHeight: 1.05,
            letterSpacing: -2,
          }}
        >
          Handy AI
        </div>
        <div
          style={{
            marginTop: 28,
            fontFamily: BODY_FONT,
            fontSize: 36,
            color: CREAM_DIM,
            lineHeight: 1.35,
          }}
        >
          An AI agent that runs your Android.
        </div>
      </div>
    </AbsoluteFill>
  );
};

const PhoneFrame: React.FC<{children: React.ReactNode}> = ({children}) => {
  // Raw recording is 720x1620 (9:19.5) → display at 680x1530 inside a bezel,
  // leaves ~180px top for caption and ~120px bottom below the phone.
  const innerW = 680;
  const innerH = 1530;
  const bezel = 24;
  const radius = 72;
  const outerW = innerW + bezel * 2;
  const outerH = innerH + bezel * 2;

  return (
    <div
      style={{
        width: outerW,
        height: outerH,
        borderRadius: radius,
        background: '#050505',
        padding: bezel,
        boxShadow:
          '0 0 0 2px rgba(237,230,214,0.08), 0 40px 120px rgba(0,0,0,0.6)',
      }}
    >
      <div
        style={{
          width: innerW,
          height: innerH,
          borderRadius: radius - bezel,
          overflow: 'hidden',
          background: INK,
          position: 'relative',
        }}
      >
        {children}
      </div>
    </div>
  );
};

const Caption: React.FC<{text: string; visible: boolean}> = ({text, visible}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const a = spring({frame, fps, config: {damping: 200}});
  return (
    <div
      style={{
        position: 'absolute',
        top: 80,
        left: 0,
        right: 0,
        textAlign: 'center',
        fontFamily: BODY_FONT,
        fontSize: 46,
        fontWeight: 500,
        color: CREAM,
        letterSpacing: -0.5,
        opacity: visible ? a : 0,
        textShadow: '0 2px 12px rgba(0,0,0,0.6)',
        padding: '0 80px',
        lineHeight: 1.25,
      }}
    >
      {text}
    </div>
  );
};

const Body: React.FC = () => {
  return (
    <AbsoluteFill style={{alignItems: 'center', justifyContent: 'flex-end', paddingBottom: 100}}>
      <Caption
        text='"Launch Chrome and navigate to news.ycombinator.com"'
        visible
      />
      <PhoneFrame>
        <OffthreadVideo
          src={staticFile('handyai-demo.mp4')}
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
          }}
        />
      </PhoneFrame>
    </AbsoluteFill>
  );
};

const Outro: React.FC = () => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const a = spring({frame, fps, config: {damping: 180, stiffness: 80}});
  const y = interpolate(a, [0, 1], [20, 0]);

  return (
    <AbsoluteFill
      style={{
        alignItems: 'center',
        justifyContent: 'center',
        color: CREAM,
        textAlign: 'center',
        padding: 80,
      }}
    >
      <div style={{transform: `translateY(${y}px)`, opacity: a}}>
        <div
          style={{
            fontFamily: BODY_FONT,
            fontSize: 32,
            color: CREAM_DIM,
            marginBottom: 28,
            letterSpacing: 2,
            textTransform: 'uppercase',
          }}
        >
          Join the beta
        </div>
        <div
          style={{
            fontFamily: DISPLAY_FONT,
            fontSize: 104,
            lineHeight: 1,
            letterSpacing: -3,
          }}
        >
          gethandyai.app
        </div>
        <div
          style={{
            marginTop: 42,
            fontFamily: BODY_FONT,
            fontSize: 30,
            color: CREAM_DIM,
          }}
        >
          Open-beta on Android · Free during beta
        </div>
      </div>
    </AbsoluteFill>
  );
};

export const HandyDemo: React.FC = () => {
  return (
    <AbsoluteFill>
      <Backdrop />
      <Sequence from={0} durationInFrames={INTRO_FRAMES}>
        <Intro />
      </Sequence>
      <Sequence from={INTRO_FRAMES} durationInFrames={BODY_FRAMES}>
        <Body />
      </Sequence>
      <Sequence
        from={INTRO_FRAMES + BODY_FRAMES}
        durationInFrames={OUTRO_FRAMES}
      >
        <Outro />
      </Sequence>
    </AbsoluteFill>
  );
};
