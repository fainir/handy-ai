import {registerRoot, Composition} from 'remotion';
import {HandyDemo, DEMO_DURATION_FRAMES, DEMO_FPS, DEMO_WIDTH, DEMO_HEIGHT} from './HandyDemo';

const Root = () => (
  <>
    <Composition
      id="HandyDemo"
      component={HandyDemo}
      durationInFrames={DEMO_DURATION_FRAMES}
      fps={DEMO_FPS}
      width={DEMO_WIDTH}
      height={DEMO_HEIGHT}
    />
  </>
);

registerRoot(Root);
